/*******************************************************************************
 * Copyright (c) 2003-2019 Maxprograms.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-v10.html
 *
 * Contributors:
 *     Maxprograms - initial API and implementation
 *******************************************************************************/
package com.maxprograms.converters.txml;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.lang.System.Logger.Level;
import java.lang.System.Logger;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLOutputter;

public class Xliff2Txml {

	private static String sklFile;
	private static String xliffFile;
	private static String catalog;
	private static String encoding;
	private static Hashtable<String, Element> segments;
	private static String outputFile;
	private static Document doc;

	private Xliff2Txml() {
		// do not instantiate this class
		// use run method instead
	}

	public static Vector<String> run(Hashtable<String, String> params) {
		Vector<String> result = new Vector<String>();

		sklFile = params.get("skeleton");
		xliffFile = params.get("xliff");
		catalog = params.get("catalog");
		encoding = params.get("encoding");
		outputFile = params.get("backfile");

		try {
			loadSegments();
			SAXBuilder builder = new SAXBuilder();
			doc = builder.build(sklFile);
			Element root = doc.getRootElement();
			replaceTargets(root);
			XMLOutputter outputter = new XMLOutputter();
			outputter.preserveSpace(true);
			outputter.setEncoding(Charset.forName(encoding));
			File f = new File(outputFile);
			if (!f.getParentFile().exists()) {
				f.getParentFile().mkdirs();
			}
			if (!f.exists()) {
				Files.createFile(Paths.get(f.toURI()));
			}
			try (FileOutputStream output = new FileOutputStream(f)) {
				outputter.output(doc, output);
			}
			result.add("0");
		} catch (IOException | SAXException | ParserConfigurationException e) {
			Logger logger = System.getLogger(Xliff2Txml.class.getName());
			logger.log(Level.ERROR, "Error merging file", e);
			result.add("1");
			if (e.getMessage() != null) {
				result.add(e.getMessage());
			} else {
				result.add("Unknown error");
			}
		}

		return result;
	}

	private static void replaceTargets(Element e) throws SAXException, IOException, ParserConfigurationException {
		if (e.getName().equals("revisions")) {
			return;
		}
		if (e.getName().equals("target")) {
			String id = e.getText().substring(3);
			id = id.substring(0, id.length() - 3);
			Element seg = segments.get(id);
			e.setText("");
			List<XMLNode> content = seg.getChild("target").getContent();
			Iterator<XMLNode> it = content.iterator();
			while (it.hasNext()) {
				XMLNode n = it.next();
				if (n.getNodeType() == XMLNode.TEXT_NODE) {
					e.addContent(((TextNode) n).getText().replaceAll("[\\n\\r]", ""));
				}
				if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
					Element tag = (Element) n;
					e.addContent(buildTag(tag.getText().replaceAll("[\\n\\r]", "")));
				}
			}
		} else {
			List<Element> children = e.getChildren();
			Iterator<Element> it = children.iterator();
			while (it.hasNext()) {
				replaceTargets(it.next());
			}
		}
	}

	private static Element buildTag(String text) throws SAXException, IOException, ParserConfigurationException {
		SAXBuilder builder = new SAXBuilder();
		Document sdoc = builder.build(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
		Element sroot = sdoc.getRootElement();
		Element tag = new Element(sroot.getName());
		tag.clone(sroot);
		return tag;
	}

	private static void loadSegments() throws SAXException, IOException, ParserConfigurationException {

		SAXBuilder builder = new SAXBuilder();
		builder.setEntityResolver(new Catalog(catalog));

		Document sdoc = builder.build(xliffFile);
		Element root = sdoc.getRootElement();
		Element body = root.getChild("file").getChild("body");
		List<Element> units = body.getChildren("trans-unit");
		Iterator<Element> i = units.iterator();

		segments = new Hashtable<String, Element>();

		while (i.hasNext()) {
			Element unit = i.next();
			segments.put(unit.getAttributeValue("id"), unit);
		}
	}
}
