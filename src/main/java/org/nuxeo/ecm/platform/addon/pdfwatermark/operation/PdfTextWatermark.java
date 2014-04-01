/*
 * (C) Copyright ${year} Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     vdutat
 */

package org.nuxeo.ecm.platform.addon.pdfwatermark.operation;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.Overlay;
import org.apache.pdfbox.exceptions.COSVisitorException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.edit.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.PDExtendedGraphicsState;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.runtime.api.Framework;

/**
 * 
 */
@Operation(id=PdfTextWatermark.ID, category=Constants.CAT_CONVERSION, 
	label="PDF Text Watermark", 
	description="Overlay a text watermark on PDF")
public class PdfTextWatermark {

    private static final String MULTIPLICITY_SINGLE = "single";

	public static final String ID = "PdfTextWatermark";

    private static final Log LOGGER = LogFactory.getLog(PdfTextWatermark.class);
	
    protected PDDocument pdfDoc;

    @Context
    protected OperationContext ctx;

    @Param(name = "text", required = true)
    protected String text = "Nuxeo";

    @Param(name = "font", required = false, widget = Constants.W_OPTION, values = {
    		"Helvetica-BoldOblique", 
    		"Times-Italic", 
    		"ZapfDingbats", 
    		"Symbol", 
    		"Helvetica-Oblique", 
    		"Courier", 
    		"Helvetica-Bold", 
    		"Helvetica", 
    		"Courier-Oblique", 
    		"Times-BoldItalic", 
    		"Courier-Bold", 
    		"Times-Roman", 
    		"Times-Bold", 
    		"Courier-BoldOblique"
    })
    protected String font = PDType1Font.TIMES_ROMAN.getBaseFont(); // TODO

    @Param(name = "fontsize", required = false)
    protected String fontSize = "25";

    @Param(name = "color", required = false)
    protected String color = "FFCC00";

    @Param(name = "angle", required = false, widget = Constants.W_OPTION, values = {
    		"0", "45", "90", "135", "225", "270", "315"
    })
    protected String angle = "0";

    @Param(name = "alpha", required = false)
    protected String alpha = "80";

    @Param(name = "multiplicity", required = false, widget = Constants.W_OPTION, values = {
    		"single", "tiled"
    })
    protected String multiplicity = MULTIPLICITY_SINGLE; // TODO

	@OperationMethod
	public Blob run(Blob input) throws IOException, COSVisitorException {
		multiplicity = "tiled";
	    File file = File.createTempFile("nxops-" + ID.toLowerCase() + "-", ".tmp");
	    FileOutputStream out = new FileOutputStream(file);
	    Framework.trackFile(file, file);
	    input.transferTo(out);
	    pdfDoc = PDDocument.load(input.getStream());
	    applyWatermark();
	    pdfDoc.save(out);
	    return new FileBlob(file, "application/pdf", null, input.getFilename(), null);
	}

	public void applyWatermark() throws COSVisitorException, IOException {
    	PDDocument watermarkDoc = getWatermark(text, font, fontSize, color, angle, alpha, multiplicity);
	    Overlay overlay = new Overlay();
	    overlay.overlay(watermarkDoc, pdfDoc);
    }
    
    /**
     * Creates the overlay PDF.
     *
     * @param text
     * @param multiplicity 
     * @param alphaStr 
     * @param angle 
     * @param color 
     * @param fontSizeTxt 
     * @param font 
     * @return PDDocument
     * @throws IOException
     * @throws COSVisitorException
     */
    public PDDocument getWatermark(String text, String font, String fontSizeTxt, String color, String angle, String alphaStr, String multiplicity) throws IOException {
        // Create a document and add a page to it
        PDPageContentStream contentStream = null;
		try {
			PDDocument document = new PDDocument();
			PDPage page = new PDPage();
			document.addPage(page);
			
			PDType1Font standardFont = PDType1Font.getStandardFont(font);
			float fontSize = Float.parseFloat(fontSizeTxt);
			float titleHeight = standardFont.getFontDescriptor().getFontBoundingBox().getHeight() / 1000 * fontSize;
			float titleWidth = standardFont.getStringWidth(text) / 1000 * fontSize;
			// TODO titleHeight and titleWidth depends on text rotation
			Double radian = new Double(Double.parseDouble(angle) * Math.PI / 180);
			Float alpha = Float.parseFloat(alphaStr) / 100;
			
			// The transparency, opacity of graphic objects can be set directly on the drawing commands
			// but need to be set to a graphic state which will become part of the
			// resources.
			
			/* Set up the graphic state */
			// Define a new extended graphic state
			PDExtendedGraphicsState extendedGraphicsState = new PDExtendedGraphicsState();
			// Set the transparency/opacity
			extendedGraphicsState.setNonStrokingAlphaConstant(alpha);
			// Get the page resources.
			Map graphicsStateDictionary = new HashMap<>();
			graphicsStateDictionary.put("TransparentState", extendedGraphicsState);
			PDResources resources = ((PDPage) pdfDoc.getDocumentCatalog().getAllPages().get(0)).findResources();
			resources.setGraphicsStates(graphicsStateDictionary);
			page.setResources(resources);
			
			// Start a new content stream which will "hold" the to be created content
			contentStream = new PDPageContentStream(document, page);
			contentStream.appendRawCommands("/TransparentState gs\n");
			contentStream.setNonStrokingColor(Color.decode("#" + color));
			
			contentStream.beginText();
			contentStream.setFont(standardFont, fontSize);
			if (MULTIPLICITY_SINGLE.equals(multiplicity)) {
				float marginTop = (page.getMediaBox().getHeight() + titleHeight) / 2;
				Float xVal = new Float((page.getMediaBox().getWidth() - titleWidth) / 2);
				Float yVal = new Float(page.getMediaBox().getHeight() - marginTop);
				// Create the text and position it
				contentStream.setTextRotation(radian, xVal, yVal);
				contentStream.drawString(text);
			} else {
				for (int xVal = 0; xVal < page.getMediaBox().getWidth(); xVal += titleWidth) {
					for (int yVal = 0; yVal < page.getMediaBox().getHeight(); yVal += titleHeight) {
						contentStream.setTextRotation(radian, xVal, yVal);
						contentStream.drawString(text);
					}
				}
			}
			contentStream.endText();
			return document;
		} finally {
			// Make sure that the content stream is closed:
			if (contentStream != null) {
				try {
					contentStream.close();
				} catch (IOException e) {
					LOGGER.error(e, e);
				}
			}
		}
    }

}
