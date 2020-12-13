/*******************************************************************************
 * Copyright (c) 2003-2020 Maxprograms.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-v10.html
 *
 * Contributors:
 *     Maxprograms - initial API and implementation
 *******************************************************************************/
package com.maxprograms.xliff2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import com.maxprograms.converters.Constants;
import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.Indenter;
import com.maxprograms.xml.PI;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLOutputter;
import com.maxprograms.xml.XMLUtils;

import org.xml.sax.SAXException;

public class FromXliff2 {

	private static String srcLang;
	private static String trgLang;

	private FromXliff2() {
		// do not instantiate this class
		// use run method instead
	}

	public static List<String> run(String sourceFile, String outputFile, String catalog) {
		List<String> result = new ArrayList<>();
		try {
			SAXBuilder builder = new SAXBuilder();
			builder.setEntityResolver(new Catalog(catalog));
			Document doc = builder.build(sourceFile);
			Element root = doc.getRootElement();
			if (!root.getAttributeValue("version").startsWith("2.")) {
				result.add(Constants.ERROR);
				result.add("Wrong XLIFF version.");
				return result;
			}
			Document xliff12 = new Document(null, "xliff", null, null);
			Element root12 = xliff12.getRootElement();
			recurse(root, root12);
			Indenter.indent(root12, 2);
			XMLOutputter outputter = new XMLOutputter();
			outputter.preserveSpace(true);
			try (FileOutputStream out = new FileOutputStream(new File(outputFile))) {
				out.write(XMLUtils.UTF8BOM);
				outputter.output(xliff12, out);
			}
			result.add(Constants.SUCCESS);
		} catch (SAXException | IOException | ParserConfigurationException | URISyntaxException ex) {
			Logger logger = System.getLogger(FromXliff2.class.getName());
			logger.log(Level.ERROR, "Error processing XLIFF 2.0", ex);
			result.add(Constants.ERROR);
			result.add(ex.getMessage());
		}
		return result;
	}

	private static void recurse(Element source, Element target) {
		if (source.getName().equals("xliff")) {
			target.setAttribute("version", "1.2");
			target.setAttribute("xmlns", "urn:oasis:names:tc:xliff:document:1.2");
			srcLang = source.getAttributeValue("srcLang");
			trgLang = source.getAttributeValue("trgLang", "");

			List<PI> encodings = source.getPI("encoding");
			if (!encodings.isEmpty()) {
				String encoding = encodings.get(0).getData();
				if (!encoding.equalsIgnoreCase("UTF-8")) {
					target.addContent(new PI("encoding", encoding));
				}
			}
		}

		if (source.getName().equals("file")) {
			Element file = new Element("file");
			file.setAttribute("original", source.getAttributeValue("original"));
			file.setAttribute("source-language", srcLang);
			if (!trgLang.isEmpty()) {
				file.setAttribute("target-language", trgLang);
			}
			Element header = new Element("header");
			file.addContent(header);
			Element body = new Element("body");
			file.addContent(body);
			Element skeleton = source.getChild("skeleton");
			if (skeleton != null) {
				Element skl = new Element("skl");
				String href = skeleton.getAttributeValue("href", "");
				if (!href.isEmpty()) {
					Element external = new Element("external-file");
					external.setAttribute("href", href);
					skl.addContent(external);
				} else {
					Element internal = new Element("internal-file");
					internal.setContent(skeleton.getContent());
					skl.addContent(internal);
				}
				header.addContent(skl);
			}
			Element metadata = source.getChild("mda:metadata");
			if (metadata != null) {
				List<Element> metadataList = metadata.getChildren("mda:metaGroup");
				for (int i = 0; i < metadataList.size(); i++) {
					Element metaGroup = metadataList.get(i);
					String category = metaGroup.getAttributeValue("category");
					if (category.equals("tool")) {
						Element tool = new Element("tool");
						header.addContent(tool);
						List<Element> metaList = metaGroup.getChildren("mda:meta");
						Iterator<Element> it = metaList.iterator();
						while (it.hasNext()) {
							Element meta = it.next();
							tool.setAttribute(meta.getAttributeValue("type"), meta.getText());
						}
					} else if (category.equals("PI")) {
						List<Element> metaList = metaGroup.getChildren("mda:meta");
						Iterator<Element> it = metaList.iterator();
						while (it.hasNext()) {
							Element meta = it.next();
							PI pi = new PI(meta.getAttributeValue("type"), meta.getText());
							file.addContent(pi);
						}
					} else if (category.equals("project-data")) {
						List<Element> metaList = metaGroup.getChildren("mda:meta");
						Iterator<Element> it = metaList.iterator();
						while (it.hasNext()) {
							Element meta = it.next();
							String type = meta.getAttributeValue("type");
							if (type.equals("project-name")) {
								file.setAttribute("product-name", meta.getText());
							}
							if (type.equals("project-id")) {
								file.setAttribute("product-version", meta.getText());
							}
							if (type.equals("build-number")) {
								file.setAttribute("build-num", meta.getText());
							}
						}
					} else if (category.equals("format")) {
						Element meta = metaGroup.getChild("mda:meta");
						file.setAttribute("datatype", meta.getText());
					} else {
						Element group = new Element("prop-group");
						group.setAttribute("name", category);
						header.addContent(group);

						List<Element> metaList = metaGroup.getChildren("mda:meta");
						Iterator<Element> it = metaList.iterator();
						while (it.hasNext()) {
							Element meta = it.next();
							Element prop = new Element("prop");
							prop.setAttribute("prop-type", meta.getAttributeValue("type"));
							prop.setContent(meta.getContent());
							group.addContent(prop);
						}
					}
				}
			}

			target.addContent(file);
			target = body;
		}

		if (source.getName().equals("group")) {
			Element group = new Element("group");
			group.setAttribute("id", source.getAttributeValue("id"));
			Element metadata = source.getChild("mda:metadata");
			if (metadata != null) {
				Element metaGroup = metadata.getChild("mda:metaGroup");
				if (metaGroup != null) {
					List<Element> metaList = metaGroup.getChildren("mda:meta");
					Iterator<Element> it = metaList.iterator();
					while (it.hasNext()) {
						Element meta = it.next();
						if (meta.getAttributeValue("type").equals("ts")) {
							group.setAttribute("ts", meta.getText());
						}
						if (meta.getAttributeValue("type").equals("space") && meta.getText().equals("keep")) {
							group.setAttribute("xml:space", "preserve");
						}
					}
				}
			}
			target.addContent(group);
			target = group;
		}

		if (source.getName().equals("unit")) {
			Element transUnit = new Element("trans-unit");
			transUnit.setAttribute("id", source.getAttributeValue("id"));
			target.addContent(transUnit);

			Map<String, String> tags = new HashMap<>();
			Element originalData = source.getChild("originalData");
			if (originalData != null) {
				List<Element> dataList = originalData.getChildren("data");
				Iterator<Element> it = dataList.iterator();
				while (it.hasNext()) {
					Element data = it.next();
					tags.put(data.getAttributeValue("id"), data.getText());
				}
			}

			Element joinedSource = new Element("source");
			Element joinedTarget = new Element("target");
			boolean approved = false;
			boolean preserve = false;

			List<Element> children = source.getChildren();
			Iterator<Element> et = children.iterator();
			while (et.hasNext()) {
				Element child = et.next();
				if (child.getName().equals("segment") || child.getName().equals("ignorable")) {
					Element src = child.getChild("source");
					if (src.getAttributeValue("xml:space", "default").equals("preserve")) {
						preserve = true;
					}
					joinedSource.addContent(src.getContent());
					Element tgt = child.getChild("target");
					if (tgt != null) {
						joinedTarget.addContent(tgt.getContent());
					}
					if (child.getName().equals("segment")) {
						if ("final".equals(child.getAttributeValue("state"))) {
							approved = true;
						}
					}
				}
			}
			if (approved) {
				transUnit.setAttribute("approved", "yes");
			}
			if (preserve) {
				transUnit.setAttribute("xml:space", "preserve");
			}

			Element src = new Element("source");
			List<XMLNode> nodes = joinedSource.getContent();
			Iterator<XMLNode> it = nodes.iterator();
			while (it.hasNext()) {
				XMLNode node = it.next();
				if (node.getNodeType() == XMLNode.TEXT_NODE) {
					src.addContent(node);
				}
				if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
					Element tag = (Element) node;
					if (tag.getName().equals("ph")) {
						Element ph = new Element("ph");
						ph.setAttribute("id", tag.getAttributeValue("id").substring("ph".length()));
						ph.addContent(tags.get(tag.getAttributeValue("id")));
						src.addContent(ph);
					}
					if (tag.getName().equals("mrk")) {
						Element mrk = new Element("mrk");
						mrk.setAttribute("mid", tag.getAttributeValue("id").substring("mrk".length()));
						mrk.setAttribute("ts", tag.getAttributeValue("value"));
						if (tag.getAttributeValue("translate", "yes").equals("no")) {
							mrk.setAttribute("mtype", "protected");
						} else {
							mrk.setAttribute("mtype", "term");
						}
						mrk.setContent(tag.getContent());
						src.addContent(mrk);
					}
				}
			}
			if (preserve) {
				transUnit.addContent("\n        ");
			}
			transUnit.addContent(src);

			Element tgt = new Element("target");
			if (!joinedTarget.getContent().isEmpty()) {
				nodes = joinedTarget.getContent();
				it = nodes.iterator();
				while (it.hasNext()) {
					XMLNode node = it.next();
					if (node.getNodeType() == XMLNode.TEXT_NODE) {
						tgt.addContent(node);
					}
					if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
						Element tag = (Element) node;
						if (tag.getName().equals("ph")) {
							Element ph = new Element("ph");
							ph.setAttribute("id", tag.getAttributeValue("id").substring("ph".length()));
							ph.addContent(tags.get(tag.getAttributeValue("id")));
							tgt.addContent(ph);
						}
						if (tag.getName().equals("mrk")) {
							Element mrk = new Element("mrk");
							mrk.setAttribute("mid", tag.getAttributeValue("id").substring("mrk".length()));
							mrk.setAttribute("ts", tag.getAttributeValue("value"));
							if (tag.getAttributeValue("translate", "yes").equals("no")) {
								mrk.setAttribute("mtype", "protected");
							} else {
								mrk.setAttribute("mtype", "term");
							}
							mrk.setContent(tag.getContent());
							tgt.addContent(mrk);
						}
					}
				}
			}
			if (!tgt.getContent().isEmpty() || approved) {
				if (preserve) {
					transUnit.addContent("\n        ");
				}
				transUnit.addContent(tgt);
			}

			Element notes = source.getChild("notes");
			if (notes != null) {
				List<Element> notesList = notes.getChildren("note");
				for (int i = 0; i < notesList.size(); i++) {
					Element note = notesList.get(i);
					Element n = new Element("note");
					String id = note.getAttributeValue("id");
					if (!id.isEmpty()) {
						n.setAttribute("id", id);
					}
					String appliesTo = note.getAttributeValue("appliesTo");
					if (!appliesTo.isEmpty()) {
						n.setAttribute("annotates", appliesTo);
					}
					n.addContent(note.getText());
					if (preserve) {
						transUnit.addContent("\n        ");
					}
					transUnit.addContent(n);
				}
			}

			Element matches = source.getChild("mtc:matches");
			if (matches != null) {
				List<Element> matchesList = matches.getChildren("mtc:match");
				for (int i = 0; i < matchesList.size(); i++) {
					Element match = matchesList.get(i);
					Element altTrans = new Element("alt-trans");
					String quality = match.getAttributeValue("matchQuality");
					if (!quality.isEmpty()) {
						try {
							float f = Float.parseFloat(quality);
							int round = Math.round(f);
							altTrans.setAttribute("match-quality", "" + round);
						} catch (NumberFormatException ne) {
							// do nothing
						}
					}
					altTrans.setAttribute("origin", match.getAttributeValue("origin", "unknown"));
					Element matchSrc = match.getChild("source");
					Element s = new Element("source");
					nodes = matchSrc.getContent();
					it = nodes.iterator();
					while (it.hasNext()) {
						XMLNode node = it.next();
						if (node.getNodeType() == XMLNode.TEXT_NODE) {
							s.addContent(node);
						}
						if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
							Element tag = (Element) node;
							if (tag.getName().equals("ph")) {
								Element ph = new Element("ph");
								String id = tag.getAttributeValue("id").substring("ph".length());
								ph.setAttribute("id", id);
								String tagContent = tags.get(tag.getAttributeValue("id"));
								if (tagContent != null) {
									ph.addContent(tagContent);
								}
								s.addContent(ph);
							}
							if (tag.getName().equals("mrk")) {
								Element mrk = new Element("mrk");
								mrk.setAttribute("mid", tag.getAttributeValue("id").substring("mrk".length()));
								mrk.setAttribute("ts", tag.getAttributeValue("value"));
								if (tag.getAttributeValue("translate", "yes").equals("no")) {
									mrk.setAttribute("mtype", "protected");
								} else {
									mrk.setAttribute("mtype", "term");
								}
								mrk.setContent(tag.getContent());
								s.addContent(mrk);
							}
						}
					}
					altTrans.addContent(s);
					Element matchTgt = match.getChild("target");
					Element t = new Element("target");
					nodes = matchTgt.getContent();
					it = nodes.iterator();
					while (it.hasNext()) {
						XMLNode node = it.next();
						if (node.getNodeType() == XMLNode.TEXT_NODE) {
							t.addContent(node);
						}
						if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
							Element tag = (Element) node;
							if (tag.getName().equals("ph")) {
								Element ph = new Element("ph");
								ph.setAttribute("id", tag.getAttributeValue("id").substring("ph".length()));
								ph.addContent(tags.get(tag.getAttributeValue("id")));
								t.addContent(ph);
							}
							if (tag.getName().equals("mrk")) {
								Element mrk = new Element("mrk");
								mrk.setAttribute("mid", tag.getAttributeValue("id").substring("mrk".length()));
								mrk.setAttribute("ts", tag.getAttributeValue("value"));
								if (tag.getAttributeValue("translate", "yes").equals("no")) {
									mrk.setAttribute("mtype", "protected");
								} else {
									mrk.setAttribute("mtype", "term");
								}
								mrk.setContent(tag.getContent());
								t.addContent(mrk);
							}
						}
					}
					altTrans.addContent(t);
					if (preserve) {
						transUnit.addContent("\n        ");
					}
					transUnit.addContent(altTrans);
				}
			}
			if (preserve) {
				transUnit.addContent("\n      ");
			}
			Indenter.indent(transUnit, 2);
		}

		List<Element> children = source.getChildren();
		Iterator<Element> it = children.iterator();
		while (it.hasNext()) {
			recurse(it.next(), target);
		}
	}
}
