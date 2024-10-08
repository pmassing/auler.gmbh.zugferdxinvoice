/******************************************************************************
 * Plug-in (auler.gmbh.zugferdxinvoice)                                       * 
 * for iDempiere * ERP & CRM Smart Business Solution                          *
 * Copyright (C) 2022  Patric Maßing (Hans Auler GmbH)                        *
 *                                                                            *
 * This plug-in is free software; you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation; either version 2 of the License, or          *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This plug-in is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License along    *
 * with this plug-in; If not, see <http://www.gnu.org/licenses/>.             *
 *                                                                            *
 * @author Diego Ruiz (BX Service GmbH)                                       *
 * 2024                                                                       *
 *****************************************************************************/
package auler.gmbh.zugferdxinvoice.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.compiere.model.MArchive;
import org.compiere.model.MAttachment;
import org.compiere.model.MAttachmentEntry;
import org.compiere.model.MInvoice;
import org.compiere.util.CLogger;
import org.compiere.util.Env;

public class FileHelper {

	private static CLogger log = CLogger.getCLogger(FileHelper.class);
	private static final String FILE_SUFFIX = "pdf";
	
	private MInvoice invoice;
	private List<File> files = new ArrayList<File>();
	
	public FileHelper(MInvoice invoice) {
		this.invoice = invoice;
	}

	public void addAttachmentFiles() {
		MAttachment m_attachment = MAttachment.get(Env.getCtx(), MInvoice.Table_ID, invoice.getC_Invoice_ID(), invoice.getC_Invoice_UU(), null);
		if (m_attachment != null) {
			for (MAttachmentEntry entry : m_attachment.getEntries()) {
				if (isPDFByExtension(entry.getName())) {
					files.add(entry.getFile());
				}
			}
		}
	}
	
	public File getDefaultAttachmentFile() {
		String filename = getDefaultFileName(invoice);
		MAttachment m_attachment = MAttachment.get(Env.getCtx(), MInvoice.Table_ID, invoice.getC_Invoice_ID(), invoice.getC_Invoice_UU(), null);
		if (m_attachment != null) {
			for (MAttachmentEntry entry : m_attachment.getEntries()) {
				if (entry.getName().equals(filename)) {
					return entry.getFile();
				}
			}
		}

		return null;
	}
	
	public void addArchivedFiles() {
		MArchive[] archives = getArchivesFromInvoice();
		
		if (archives != null && archives.length > 0) {
			for (MArchive archive : archives) {
				if (isPDFByExtension(archive.getName()))
					files.add(getArchiveFile(archive));
			}
		}
	}
	
	private MArchive[] getArchivesFromInvoice() {
		StringBuilder sqlWhere = new StringBuilder(" AND AD_Table_ID=")
				.append(invoice.get_Table_ID())
				.append(" AND Record_ID=").append(invoice.getC_Invoice_ID());
		
		return MArchive.get(Env.getCtx(), sqlWhere.toString(), null);
	}
	
	private File getArchiveFile(MArchive archive) {
		File pdfFile = null;
		try {
			pdfFile = new File(archive.getName());
			Files.write(pdfFile.toPath(), archive.getBinaryData());
		} catch (IOException e) {
			log.severe(e.getMessage());
		}
		return pdfFile;
	}
	
	public static boolean isPDFByExtension(String fileName) {
	    fileName = fileName.toLowerCase();
	    return fileName.endsWith("." + FILE_SUFFIX);
	}
	
	public static String getDefaultFileName(MInvoice invoice) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		return invoice.getDocumentNo() + "-" + sdf.format(invoice.getDateInvoiced()) + "." + FILE_SUFFIX;
	}

	public List<File> getFiles() {
		return files;
	}

	public void clearFiles() {
		files.clear();
	}
}
