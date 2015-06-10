package org.nuxeo.ecm.platform.addon.pdfwatermark.operation;

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
import org.apache.pdfbox.pdmodel.graphics.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDJpeg;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDXObjectImage;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.runtime.api.Framework;

@Operation(id = PdfImageWatermark.ID, category = Constants.CAT_CONVERSION, label = "PDF Image Watermark", description = "Overlay an image watermark on PDF")
public class PdfImageWatermark {

    private static final String MULTIPLICITY_SINGLE = "single";

    private static final Log LOGGER = LogFactory.getLog(PdfImageWatermark.class);

    protected PDDocument pdfDoc;

    public static final String ID = "PdfImageWatermark";

    @Context
    protected OperationContext ctx;

    @Param(name = "imageBlob", required = true)
    protected Blob imageBlob;

    @Param(name = "alpha", required = false)
    protected String alpha = "80";

    @Param(name = "multiplicity", required = false, widget = Constants.W_OPTION, values = { "single", "tiled" })
    protected String multiplicity = MULTIPLICITY_SINGLE;

    @OperationMethod
    public Blob run(Blob input) throws IOException, COSVisitorException {
        File file = File.createTempFile("nxops-" + ID.toLowerCase() + "-", ".tmp");
        FileOutputStream out = new FileOutputStream(file);
        Framework.trackFile(file, file);
        input.transferTo(out);
        pdfDoc = PDDocument.load(input.getStream());
        applyWatermark();
        pdfDoc.save(out);
        return new FileBlob(file, "application/pdf", null, input.getFilename(), null);
    }

    private void applyWatermark() throws COSVisitorException, IOException {
        PDDocument watermarkDoc = null;
        PDPageContentStream contentStream = null;
        try {
            /*
             * Step 1: Prepare the document.
             */
            watermarkDoc = new PDDocument();
            PDPage page = new PDPage();
            watermarkDoc.addPage(page);
            /*
             * Step 2: Prepare the image PDJpeg is the class you use when
             * dealing with jpg images. You will need to mention the jpg file
             * and the document to which it is to be added Note that if you
             * complete these steps after the creating the content stream the
             * PDF file created will show "Out of memory" error.
             */
            PDXObjectImage image = null;
            image = new PDJpeg(watermarkDoc, imageBlob.getStream());
            /*
             * Step 3: Add (draw) the image to the content stream mentioning the
             * position where it should be drawn and leaving the size of the
             * image as it is
             */
            Float alphaVal = Float.parseFloat(alpha) / 100;

            // The transparency, opacity of graphic objects can be set directly
            // on the drawing commands
            // but need to be set to a graphic state which will become part of
            // the
            // resources.

            /* Set up the graphic state */
            // Define a new extended graphic state
            PDExtendedGraphicsState extendedGraphicsState = new PDExtendedGraphicsState();
            // Set the transparency/opacity
            extendedGraphicsState.setNonStrokingAlphaConstant(alphaVal);
            // Get the page resources.
            Map graphicsStateDictionary = new HashMap<>();
            graphicsStateDictionary.put("TransparentState", extendedGraphicsState);
            PDResources resources = ((PDPage) pdfDoc.getDocumentCatalog().getAllPages().get(0)).findResources();
            resources.setGraphicsStates(graphicsStateDictionary);
            page.setResources(resources);

            /*
             * Create a content stream mentioning the document, the page in the
             * dcoument where the content stream is to be added. Note that this
             * step has to be completed after the above two steps are complete.
             */
            contentStream = new PDPageContentStream(watermarkDoc, page);
            contentStream.appendRawCommands("/TransparentState gs\n");

            if (MULTIPLICITY_SINGLE.equals(multiplicity)) {
                for (int xVal = 0; xVal < page.getMediaBox().getWidth(); xVal += image.getWidth()) {
                    for (int yVal = 0; yVal < page.getMediaBox().getHeight(); yVal += image.getHeight()) {
                        contentStream.drawImage(image, xVal, yVal);
                    }
                }
            } else {
                float marginTop = (page.getMediaBox().getHeight() + image.getHeight()) / 2;
                Float xVal = new Float((page.getMediaBox().getWidth() - image.getWidth()) / 2);
                Float yVal = new Float(page.getMediaBox().getHeight() - marginTop);
                contentStream.drawImage(image, xVal, yVal);
            }
            /*
             * Step 4: Save the document as a pdf file mentioning the name of
             * the file
             */
            // File file = File.createTempFile("nxops-" + ID.toLowerCase() +
            // "-overlay-", ".tmp");
            // FileOutputStream out = new FileOutputStream(file);
            // watermarkDoc.save(out);
            Overlay overlay = new Overlay();
            overlay.overlay(watermarkDoc, pdfDoc);
        } catch (Exception e) {
            LOGGER.error(e, e);
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
