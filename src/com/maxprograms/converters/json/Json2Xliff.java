/*******************************************************************************
 * Copyright (c) 2022 Maxprograms.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-v10.html
 *
 * Contributors:
 *     Maxprograms - initial API and implementation
 *******************************************************************************/

package com.maxprograms.converters.json;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import com.maxprograms.converters.Constants;
import com.maxprograms.converters.EncodingResolver;
import com.maxprograms.converters.Utils;
import com.maxprograms.segmenter.Segmenter;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;

public class Json2Xliff {

    private static boolean paragraphSegmentation;
    private static Segmenter segmenter;
    private static int id;
    private static List<Element> segments;
    private static int bomLength = 0;

    private Json2Xliff() {
        // do not instantiate this class
        // use run method instead
    }

    public static List<String> run(Map<String, String> params) {
        List<String> result = new ArrayList<>();

        id = 0;
        segments = new ArrayList<>();

        String inputFile = params.get("source");
        String xliffFile = params.get("xliff");
        String skeletonFile = params.get("skeleton");
        String sourceLanguage = params.get("srcLang");
        String targetLanguage = params.get("tgtLang");
        String encoding = params.get("srcEncoding");
        String paragraph = params.get("paragraph");
        paragraphSegmentation = "yes".equals(paragraph);
        String initSegmenter = params.get("srxFile");
        String catalog = params.get("catalog");
        String tgtLang = "";
        if (targetLanguage != null) {
            tgtLang = "\" target-language=\"" + targetLanguage;
        }
        try {
            bomLength = EncodingResolver.getBOM(inputFile) == null ? 0 : 1;
            Object json = loadFile(inputFile, encoding);
            if (!paragraphSegmentation) {
                segmenter = new Segmenter(initSegmenter, sourceLanguage, catalog);
            }
            String configFile = params.get("config");
            if (configFile != null) {
                JsonConfig config = JsonConfig.parseFile(configFile);
                if (json instanceof JSONObject obj) {
                    parseJson(obj, config);
                } else {
                    parseArray((JSONArray) json, config);
                }
            } else {
                if (json instanceof JSONObject obj) {
                    parseJson(obj);
                } else {
                    parseArray((JSONArray) json);
                }
            }

            if (segments.isEmpty()) {
                result.add(Constants.ERROR);
                result.add("Nothing to translate.");
                return result;
            }

            try (FileOutputStream out = new FileOutputStream(skeletonFile)) {
                if (json instanceof JSONObject obj) {
                    out.write(obj.toString(2).getBytes(StandardCharsets.UTF_8));
                } else {
                    out.write(((JSONArray) json).toString(2).getBytes(StandardCharsets.UTF_8));
                }
            }

            try (FileOutputStream out = new FileOutputStream(xliffFile)) {

                writeString(out, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                writeString(out, "<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\" "
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:schemaLocation=\"urn:oasis:names:tc:xliff:document:1.2 xliff-core-1.2-transitional.xsd\">\n");

                writeString(out, "<file original=\"" + inputFile + "\" source-language=\"" + sourceLanguage + tgtLang
                        + "\" tool-id=\"" + Constants.TOOLID + "\" datatype=\"x-json\">\n");
                writeString(out, "<header>\n");
                writeString(out, "   <skl>\n");
                writeString(out, "      <external-file href=\"" + Utils.cleanString(skeletonFile) + "\"/>\n");
                writeString(out, "   </skl>\n");
                writeString(out, "   <tool tool-version=\"" + Constants.VERSION + " " + Constants.BUILD
                        + "\" tool-id=\"" + Constants.TOOLID + "\" tool-name=\"" + Constants.TOOLNAME + "\"/>\n");
                writeString(out, "</header>\n");
                writeString(out, "<?encoding " + encoding + "?>\n");
                writeString(out, "<body>\n");

                for (int i = 0; i < segments.size(); i++) {
                    writeString(out, "  " + segments.get(i).toString() + "\n  ");
                }

                writeString(out, "</body>\n");
                writeString(out, "</file>\n");
                writeString(out, "</xliff>");
            }

            result.add(Constants.SUCCESS);
        } catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
            Logger logger = System.getLogger(Json2Xliff.class.getName());
            logger.log(Level.ERROR, "Error converting JSON file.", e);
            result.add(Constants.ERROR);
            result.add(e.getMessage());
        }
        return result;
    }

    private static void writeString(FileOutputStream out, String string) throws IOException {
        out.write(string.getBytes(StandardCharsets.UTF_8));
    }

    protected static Object loadFile(String file, String charset) throws IOException {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        try (FileReader stream = new FileReader(new File(file), Charset.forName(charset))) {
            try (BufferedReader reader = new BufferedReader(stream)) {
                String line = "";
                while ((line = reader.readLine()) != null) {
                    if (!first) {
                        builder.append('\n');
                    }
                    builder.append(line);
                    first = false;
                }
            }
        }
        for (int i = bomLength; i < builder.length(); i++) {
            if (builder.charAt(i) == '[') {
                return new JSONArray(builder.toString().substring(bomLength));
            }
            if (builder.charAt(i) == '{') {
                return new JSONObject(builder.toString().substring(bomLength));
            }
            if (!Character.isSpaceChar(builder.charAt(i))) {
                break;
            }
        }
        throw new IOException("Selected file is not supported");
    }

    private static void parseJson(JSONObject json) {
        Iterator<String> it = json.keys();
        while (it.hasNext()) {
            String key = it.next();
            Object obj = json.get(key);
            if (obj instanceof JSONObject js) {
                parseJson(js);
            } else if (obj instanceof String string) {
                json.put(key, parseText(string));
            } else if (obj instanceof JSONArray array) {
                parseArray(array);
            }
        }
    }

    private static void parseJson(JSONObject json, JsonConfig config) throws IOException {
        List<String> translatableKeys = config.getSourceKeys();
        List<String> ignorable = config.getIgnorableKeys();
        Set<String> parsedKeys = new HashSet<>();
        for (int i = 0; i < translatableKeys.size(); i++) {
            String sourceKey = translatableKeys.get(i);
            if (json.has(sourceKey)) {
                JSONObject configuration = config.getConfiguration(sourceKey);
                if (configuration == null) {
                    throw new IOException("Wrong configuration for source key " + sourceKey);
                }
                String sourceText = json.getString(sourceKey);
                String targetKey = configuration.has(JsonConfig.TARGETKEY)
                        ? configuration.getString(JsonConfig.TARGETKEY)
                        : "";
                String targetText = json.has(targetKey) ? json.getString(targetKey) : "";
                String idKey = configuration.has(JsonConfig.IDKEY) ? configuration.getString(JsonConfig.IDKEY) : "";
                String id = json.has(idKey) ? json.getString(idKey) : "";
                String noteKey = configuration.has(JsonConfig.NOTEKEY) ? configuration.getString(JsonConfig.NOTEKEY)
                        : "";
                String[] notes = configuration.has(noteKey) ? harvestNotes(configuration.get(noteKey))
                        : new String[] {};
                parsedKeys.add(sourceKey);
                if (!targetKey.isEmpty()) {
                    parsedKeys.add(targetKey);
                }
                if (!idKey.isEmpty()) {
                    parsedKeys.add(idKey);
                }
                if (!noteKey.isEmpty()) {
                    parsedKeys.add(noteKey);
                }
                Element unit = makeUnit(sourceText, targetText, id, notes);
            }
        }
        Iterator<String> it = json.keys();
        while (it.hasNext()) {
            String key = it.next();
            if (!parsedKeys.contains(key) && !ignorable.contains(key)) {
                Object object = json.get(key);
                if (object instanceof JSONObject jsobj) {
                    parseJson(jsobj, config);
                } else if (object instanceof String string) {
                    json.put(key, string);
                } else if (object instanceof JSONArray array) {
                    parseArray(array, config);
                }
            }
        }
    }

    private static Element makeUnit(String sourceText, String targetText, String id, String[] notes)
            throws IOException {
        if (!id.isEmpty()) {
            validateId(id);
        }
        return null;
    }

    private static void validateId(String id) throws IOException {
        String[] nameStart = new String[] { ":", "[A-Z]", "_", "[a-z]", "[\\u00C0-\\u00D6]", "[\\u00D8-\\u00F6]",
                "[\\u00F8-\\u02FF]", "[\\u0370-\\u037D]", "[\\u037F-\\u1FFF]", "[\\u200C-\\u200D]", "[\\u2070-\\u218F]",
                "[\\u2C00-\\u2FEF]", "[\\u3001-\\uD7FF]", "[\\uF900-\\uFDCF]", "[\\uFDF0-\\uFFFD]",
                "[\\u10000-\\uEFFFF]" };
        String[] nameChar = new String[] { ":", "[A-Z]", "_", "[a-z]", "[-]", "[.]", "[0-9]", "\u00B7",
                "[\\u00C0-\\u00D6]", "[\\u00D8-\\u00F6]", "[\\u00F8-\\u02FF]", "[\\u0370-\\u037D]", "[\\u037F-\\u1FFF]",
                "[\\u200C-\\u200D]", "[\\u2070-\\u218F]", "[\\u2C00-\\u2FEF]", "[\\u3001-\\uD7FF]", "[\\uF900-\\uFDCF]",
                "[\\uFDF0-\\uFFFD]", "[\\u10000-\\uEFFFF]", "[\\u0300-\\u036F]", "[\\u203F-\\u2040]" };
        boolean first = false;
        String firstChar = "" + id.charAt(0);
        for (int i = 0; i < nameStart.length; i++) {
            if (firstChar.matches(nameStart[i])) {
                first = true;
                break;
            }
        }
        if (!first) {
            throw new IOException("Invalid initial character for \"id\": " + id.charAt(0));
        }
        for (int i = 1; i < id.length(); i++) {
            boolean rest = false;
            String nextChar = "" + id.charAt(i);
            for (int j = 0; j < nameStart.length; j++) {
                String expr = nameChar[j];
                if (nextChar.matches(expr)) {
                    rest = true;
                    break;
                }
            }
            if (!rest) {
                throw new IOException("Invalid character for \"id\": " + id.charAt(i));
            }
        }
    }

    private static String[] harvestNotes(Object object) {
        return new String[] {};
    }

    private static String parseText(String string) {
        if (!paragraphSegmentation) {
            String[] segs = segmenter.segment(string);
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < segs.length; i++) {
                result.append(addSegment(segs[i]));
            }
            return result.toString();
        }
        return addSegment(string);
    }

    private static String addSegment(String string) {
        Element segment = new Element("trans-unit");
        segment.setAttribute("id", "" + id);
        segment.addContent("\n    ");
        Element source = new Element("source");
        source.setText(string);
        segment.addContent(source);
        fixHtmlTags(source);
        String start = "";
        String end = "";
        if (!source.getChildren().isEmpty()) {
            int tagCount = source.getChildren().size();
            List<XMLNode> content = source.getContent();
            if (tagCount == 1) {
                if (content.get(0).getNodeType() == XMLNode.ELEMENT_NODE) {
                    Element startTag = (Element) content.get(0);
                    start = startTag.getText();
                    content.remove(startTag);
                    source.setContent(content);
                }
                if (content.get(content.size() - 1).getNodeType() == XMLNode.ELEMENT_NODE) {
                    Element endTag = (Element) content.get(content.size() - 1);
                    end = endTag.getText();
                    content.remove(endTag);
                    source.setContent(content);
                }
            }
            if (tagCount == 2 && content.get(0).getNodeType() == XMLNode.ELEMENT_NODE
                    && content.get(content.size() - 1).getNodeType() == XMLNode.ELEMENT_NODE) {
                Element startTag = (Element) content.get(0);
                start = startTag.getText();
                Element endTag = (Element) content.get(content.size() - 1);
                end = endTag.getText();
                content.remove(endTag);
                content.remove(startTag);
                source.setContent(content);
            }
        } else {
            // restore spaces normalized when fixing HTML tags
            source.setText(string);
            segment.setAttribute("xml:space", "preserve");
        }
        segment.addContent("\n  ");
        segments.add(segment);
        return start + "%%%" + id++ + "%%%" + end;
    }

    private static void parseArray(JSONArray array) {
        for (int i = 0; i < array.length(); i++) {
            Object obj = array.get(i);
            if (obj instanceof String string) {
                array.put(i, parseText(string));
            } else if (obj instanceof JSONArray arr) {
                parseArray(arr);
            } else if (obj instanceof JSONObject json) {
                parseJson(json);
            }
        }
    }

    private static void parseArray(JSONArray array, JsonConfig config) throws JSONException, IOException {
        // TODO check nested translatable objects
        for (int i = 0; i < array.length(); i++) {
            Object obj = array.get(i);
            if (obj instanceof String string) {
                array.put(i, parseText(string));
            } else if (obj instanceof JSONArray arr) {
                parseArray(arr, config);
            } else if (obj instanceof JSONObject json) {
                parseJson(json, config);
            }
        }
    }

    private static void fixHtmlTags(Element src) {
        int count = 0;
        Pattern pattern = Pattern.compile("<[A-Za-z0-9]+([\\s][A-Za-z\\-\\.]+=[\"|\'][^<&>]*[\"|\'])*[\\s]*/?>");
        Pattern endPattern = Pattern.compile("</[A-Za-z0-9]+>");

        String e = normalise(src.getText());

        Matcher matcher = pattern.matcher(e);
        if (matcher.find()) {
            List<XMLNode> newContent = new Vector<>();
            List<XMLNode> content = src.getContent();
            Iterator<XMLNode> it = content.iterator();
            while (it.hasNext()) {
                XMLNode node = it.next();
                if (node.getNodeType() == XMLNode.TEXT_NODE) {
                    TextNode t = (TextNode) node;
                    String text = normalise(t.getText());
                    matcher = pattern.matcher(text);
                    if (matcher.find()) {
                        matcher.reset();
                        while (matcher.find()) {
                            int start = matcher.start();
                            int end = matcher.end();

                            String s = text.substring(0, start);
                            if (!s.isEmpty()) {
                                newContent.add(new TextNode(s));
                            }
                            String tag = text.substring(start, end);
                            Element ph = new Element("ph");
                            ph.setAttribute("id", "" + count++);
                            ph.setText(tag);
                            newContent.add(ph);

                            text = text.substring(end);
                            matcher = pattern.matcher(text);
                        }
                        if (!text.isEmpty()) {
                            newContent.add(new TextNode(text));
                        }
                    } else {
                        if (!((TextNode) node).getText().isEmpty()) {
                            newContent.add(node);
                        }
                    }
                } else {
                    newContent.add(node);
                }
            }
            src.setContent(newContent);
        }
        matcher = endPattern.matcher(e);
        if (matcher.find()) {
            List<XMLNode> newContent = new Vector<>();
            List<XMLNode> content = src.getContent();
            Iterator<XMLNode> it = content.iterator();
            while (it.hasNext()) {
                XMLNode node = it.next();
                if (node.getNodeType() == XMLNode.TEXT_NODE) {
                    TextNode t = (TextNode) node;
                    String text = normalise(t.getText());
                    matcher = endPattern.matcher(text);
                    if (matcher.find()) {
                        matcher.reset();
                        while (matcher.find()) {
                            int start = matcher.start();
                            int end = matcher.end();

                            String s = text.substring(0, start);
                            if (!s.isEmpty()) {
                                newContent.add(new TextNode(s));
                            }

                            String tag = text.substring(start, end);
                            Element ph = new Element("ph");
                            ph.setAttribute("id", "" + count++);
                            ph.setText(tag);
                            newContent.add(ph);

                            text = text.substring(end);
                            matcher = endPattern.matcher(text);
                        }
                        if (!text.isEmpty()) {
                            newContent.add(new TextNode(text));
                        }
                    } else {
                        if (!((TextNode) node).getText().isEmpty()) {
                            newContent.add(node);
                        }
                    }
                } else {
                    newContent.add(node);
                }
            }
            src.setContent(newContent);
        }
    }

    private static String normalise(String string) {
        String result = string;
        result = result.replace('\n', ' ');
        result = result.replaceAll("\\s(\\s)+", " ");
        return result;
    }

}
