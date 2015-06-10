package org.nuxeo.ecm.platform.addon.pdfwatermark.operation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.pdfbox.Overlay;
import org.apache.pdfbox.exceptions.COSVisitorException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.runtime.api.Framework;

public abstract class AbstractPdfWatermark {

    public static final String ID = "PdfWatermark";
	
    protected PDDocument realDoc;
    
	public abstract PDDocument applyWatermark() throws COSVisitorException,
			IOException;

	@OperationMethod
	public Blob run(Blob input) throws IOException, COSVisitorException {
	    File file = File.createTempFile("nxops-" + ID.toLowerCase() + "-", ".tmp");
	    FileOutputStream out = new FileOutputStream(file);
	    Framework.trackFile(file, file);
	    input.transferTo(out);
	    realDoc = PDDocument.load(input.getStream());
	    PDDocument watermarkDoc = applyWatermark();
	    Overlay overlay = new Overlay();
	    overlay.overlay(watermarkDoc, realDoc);
	    realDoc.save(out);
	    return new FileBlob(file, "application/pdf", null, input.getFilename(), null);
	}

}