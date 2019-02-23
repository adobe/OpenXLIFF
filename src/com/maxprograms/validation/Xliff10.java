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

package com.maxprograms.validation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLOutputter;

public class Xliff10 {

	private static Logger LOGGER = System.getLogger(Xliff10.class.getName());
	private String reason = "";

	public boolean validate(Document document, String catalog) {
		if (document.getSystemId() == null && document.getPublicId() == null) {
			File temp;
			try {
				temp = File.createTempFile("temp", ".xlf");
				temp.deleteOnExit();
				document.setPublicId("-//XLIFF//DTD XLIFF//EN");
				document.setSystemId("xliff.dtd");
				XMLOutputter outputter = new XMLOutputter();
				try (FileOutputStream output = new FileOutputStream(temp)) {
					outputter.output(document, output);
				}
			} catch (IOException e) {
				LOGGER.log(Level.ERROR, e);
				reason = "Error adding DTD declaration";
				return false;
			}
			try {
				SAXBuilder builder = new SAXBuilder();
				builder.setValidating(true);
				builder.setEntityResolver(new Catalog(catalog));
				builder.build(temp);
			} catch (IOException | SAXException | ParserConfigurationException e) {
				LOGGER.log(Level.ERROR, e);
				reason = e.getMessage();
				return false;
			}
		}
		return true;
	}

	public String getReason() {
		return reason;
	}

}
