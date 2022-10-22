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
package com.maxprograms.converters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.xml.Attribute;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.Indenter;
import com.maxprograms.xml.SAXBuilder;

public class Join {

	private Join() {
		// do not instantiate this class
	}

	private static Logger logger = System.getLogger(Join.class.getName());

	private static List<String> list;
	private static String target = "";

	public static void main(String[] args) {
		String[] arguments = Utils.fixPath(args);
		if (arguments.length < 4) {
			help();
			return;
		}
		for (int i = 0; i < arguments.length; i++) {
			String arg = arguments[i];
			if (arg.equals("-help")) {
				help();
				return;
			}
			if (arg.equals("-files") && (i + 1) < arguments.length) {
				String files = arguments[i + 1];
				String[] array = files.split("\\,");
				list = new ArrayList<>();
				for (int h = 0; h < array.length; h++) {
					String file = array[h];
					File f = new File(file);
					if (!f.exists()) {
						logger.log(Level.ERROR, "File '" + file + "' does not exist.'");
						System.exit(1);
					}
					list.add(array[h]);
				}
			}
			if (arg.equals("-target") && (i + 1) < arguments.length) {
				target = arguments[i + 1];
			}
		}
		try {
			join(list, target);
		} catch (IOException | SAXException | ParserConfigurationException ex) {
			logger.log(Level.ERROR, ex.getMessage(), ex);
		}
	}

	private static void help() {
		String launcher = "   join.sh ";
		if (System.getProperty("file.separator").equals("\\")) {
			launcher = "   join.bat ";
		}
		String help = "Usage:\n\n" + launcher + "[-help] -target targetFile -files file1,file2,file3... \n\n Where:\n\n"
				+ "   -help:     (optional) Display this help information and exit\n"
				+ "   -target:   combined output XLIFF file\n"
				+ "   -files:    list of XLIFF files to join, separated by ','";
		System.out.println(help);
	}

	public static void join(List<String> xliffs, String out)
			throws IOException, SAXException, ParserConfigurationException {
		SAXBuilder builder = new SAXBuilder();
		String version = "";
		String srcLang = "";
		String trgLang = "";
		try (FileOutputStream output = new FileOutputStream(out)) {
			Map<String, Attribute> spaces = new HashMap<>();
			TreeSet<String> set = new TreeSet<>();
			for (int i = 0; i < xliffs.size(); i++) {
				Document doc = builder.build(xliffs.get(i));
				Element root = doc.getRootElement();
				String v = root.getAttributeValue("version");
				if (version.isEmpty()) {
					version = v;
				} else {
					if (!version.equals(v)) {
						throw new IOException("XLIFF files from different versions");
					}
				}
				if (version.startsWith("2.")) {
					String src = root.getAttributeValue("srcLang");
					if (srcLang.isEmpty()) {
						srcLang = src;
					} else {
						if (!srcLang.equals(src)) {
							throw new IOException("XLIFF files with different source language");
						}
					}
					String trg = root.getAttributeValue("trgLang");
					if (trgLang.isEmpty()) {
						trgLang = trg;
					}
					if (!trgLang.equals(trg)) {
						throw new IOException("XLIFF files with different target language");
					}
				}
				List<Attribute> atts = root.getAttributes();
				Iterator<Attribute> at = atts.iterator();
				while (at.hasNext()) {
					Attribute a = at.next();
					if (!a.getNamespace().isEmpty() || "xmlns".equals(a.getName())) {
						spaces.put(a.getName(), a);
					}
				}
				List<Element> fileList = root.getChildren("file");
				for (int j = 0; j < fileList.size(); j++) {
					Element file = fileList.get(j);
					String original = file.getAttributeValue("original");
					set.add(original);
				}
			}
			String treeRoot = findTreeRoot(set);

			writeString(output, "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
			writeString(output, "<xliff version=\"" + version + "\"");
			if (version.startsWith("2.")) {
				writeString(output, " srcLang=\"" + srcLang + "\"");
				if (!trgLang.isEmpty()) {
					writeString(output, " trgLang=\"" + trgLang + "\"");
				}
			}
			Set<String> keys = spaces.keySet();
			Iterator<String> kt = keys.iterator();
			while (kt.hasNext()) {
				Attribute a = spaces.get(kt.next());
				writeString(output, " " + a.toString());
			}
			writeString(output, ">\n");

			Iterator<String> it = xliffs.iterator();
			int count = 0;
			while (it.hasNext()) {
				String xliff = it.next();
				Document doc = builder.build(xliff);
				Element root = doc.getRootElement();
				List<Element> files1 = root.getChildren("file");

				for (int i = 0; i < files1.size(); i++) {
					Element file = files1.get(i);
					String original = file.getAttributeValue("original");
					file.setAttribute("original", Utils.makeRelativePath(treeRoot, original));
					if (version.startsWith("2")) {
						file.setAttribute("id", "" + count++);
					}
					Indenter.indent(file, 2, 2);
					writeString(output, "  ");
					file.writeBytes(output, StandardCharsets.UTF_8);
					writeString(output, "\n");
				}
			}
			writeString(output, "</xliff>");
		}
	}

	private static void writeString(FileOutputStream output, String string) throws IOException {
		output.write(string.getBytes(StandardCharsets.UTF_8));
	}

	public static String findTreeRoot(SortedSet<String> set) {
		StringBuilder result = new StringBuilder();
		MTree<String> tree = filesTree(set);
		MTree.Node<String> root = tree.getRoot();
		while (root.size() == 1) {
			result.append(root.getData());
			root = root.getChild(0);
		}
		return result.toString();
	}

	private static MTree<String> filesTree(SortedSet<String> files) {
		MTree<String> result = new MTree<>("");
		Iterator<String> it = files.iterator();
		while (it.hasNext()) {
			String s = it.next();
			StringTokenizer st = new StringTokenizer(s, "/\\:", true);
			MTree.Node<String> current = result.getRoot();
			while (st.hasMoreTokens()) {
				String name = st.nextToken();
				MTree.Node<String> level1 = current.getChild(name);
				if (level1 != null) {
					current = level1;
				} else {
					current.addChild(new MTree.Node<>(name));
					current = current.getChild(name);
				}
			}
		}
		return result;
	}
}
