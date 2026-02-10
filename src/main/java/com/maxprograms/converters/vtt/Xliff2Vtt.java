package com.maxprograms.converters.vtt;

import com.maxprograms.converters.Constants;
import com.maxprograms.xml.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Xliff2Vtt {

    private static String xliffFile;
    private static Catalog catalog;
    private static Map<String, Element> segments;
    private static FileOutputStream output;

    private Xliff2Vtt() {
        // do not instantiate this class
        // use run method instead
    }

    public static List<String> run(Map<String, String> params) {
        List<String> result = new ArrayList<>();

        try {
            String sklFile = params.get("skeleton");
            xliffFile = params.get("xliff");
            
            initializeCatalog(params.get("catalog"));
            setupOutputFile(params.get("backfile"));
            loadSegments();
            
            result = processSkeleton(sklFile);
            
            output.close();
            if (result.isEmpty()) {
                result.add(Constants.SUCCESS);
            }
        } catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
            Logger logger = LoggerFactory.getLogger(Xliff2Vtt.class.getName());
            logger.error("Error merging VTT file", e);
            result.clear();
            result.add(Constants.ERROR);
            result.add(e.getMessage());
        }
        return result;
    }

    private static void initializeCatalog(String catalogPath) throws SAXException, IOException, ParserConfigurationException, URISyntaxException {
        catalog = new Catalog(catalogPath);
    }

    private static void setupOutputFile(String outputFilePath) throws IOException, URISyntaxException {
        File outputFile = new File(outputFilePath);
        File parentDir = outputFile.getParentFile();
        
        if (parentDir == null) {
            parentDir = new File(System.getProperty("user.dir"));
        }
        
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        if (!outputFile.exists()) {
            Files.createFile(Paths.get(outputFile.toURI()));
        }
        
        output = new FileOutputStream(outputFile);
    }

    private static List<String> processSkeleton(String sklFile) throws IOException {
        List<String> result = new ArrayList<>();
        
        try (FileReader reader = new FileReader(sklFile);
             BufferedReader buffer = new BufferedReader(reader)) {
            
            String line;
            while ((line = buffer.readLine()) != null) {
                line = line + "\n";
                List<String> processResult = processSkeletonLine(line);
                if (!processResult.isEmpty()) {
                    return processResult; // Error occurred
                }
            }
        }
        return result;
    }

    private static List<String> processSkeletonLine(String line) throws IOException {
        List<String> result = new ArrayList<>();
        
        if (!line.contains("%%%")) {
            writeString(line);
            return result;
        }
        
        int index = line.indexOf("%%%");
        while (index != -1) {
            // Write text before the placeholder
            String beforePlaceholder = line.substring(0, index);
            writeString(beforePlaceholder);
            
            // Extract segment code
            line = line.substring(index + 3);
            String segmentCode = line.substring(0, line.indexOf("%%%"));
            line = line.substring(line.indexOf("%%%") + 3);
            
            // Process segment
            List<String> segmentResult = processSegment(segmentCode);
            if (!segmentResult.isEmpty()) {
                return segmentResult; // Error occurred
            }
            
            // Look for next placeholder
            index = line.indexOf("%%%");
            if (index == -1) {
                writeString(line);
            }
        }
        
        return result;
    }

    private static List<String> processSegment(String segmentCode) throws IOException {
        List<String> result = new ArrayList<>();
        
        Element segment = segments.get(segmentCode);
        if (segment == null) {
            result.add(Constants.ERROR);
            result.add("segment " + segmentCode + " not found");
            return result;
        }
        
        String textToWrite = getSegmentText(segment);
        writeString(textToWrite);
        
        return result;
    }

    private static String getSegmentText(Element segment) {
        Element target = segment.getChild("target");
        Element source = segment.getChild("source");
        
        // Use target if it exists and is approved, otherwise use source
        if (target != null && segment.getAttributeValue("approved", "no").equals("yes")) {
            return extractText(target);
        } else {
            return extractText(source);
        }
    }

    private static void writeString(String string) throws IOException {
        output.write(string.getBytes(StandardCharsets.UTF_8));
    }

    private static void loadSegments() throws SAXException, IOException, ParserConfigurationException {
        SAXBuilder builder = new SAXBuilder();
        if (catalog != null) {
            builder.setEntityResolver(catalog);
        }

        Document doc = builder.build(xliffFile);
        Element root = doc.getRootElement();
        Element body = root.getChild("file").getChild("body");
        List<Element> units = body.getChildren("trans-unit");
        Iterator<Element> i = units.iterator();

        segments = new HashMap<>();

        while (i.hasNext()) {
            Element unit = i.next();
            segments.put(unit.getAttributeValue("id"), unit);
        }
    }

    private static String extractText(Element target) {
        StringBuilder result = new StringBuilder();
        List<XMLNode> content = target.getContent();
        Iterator<XMLNode> i = content.iterator();
        while (i.hasNext()) {
            XMLNode n = i.next();
            if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
                Element e = (Element) n;
                result.append(extractText(e));
            }
            if (n.getNodeType() == XMLNode.TEXT_NODE) {
                result.append(((TextNode) n).getText());
            }
        }
        return result.toString();
    }
}
