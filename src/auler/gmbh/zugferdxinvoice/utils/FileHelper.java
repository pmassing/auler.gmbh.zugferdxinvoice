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
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MArchive;
import org.compiere.model.MAttachment;
import org.compiere.model.MAttachmentEntry;
import org.compiere.model.MBPartner;
import org.compiere.model.MInvoice;
import org.compiere.model.MSysConfig;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Util;

public class FileHelper {

	private static CLogger log = CLogger.getCLogger(FileHelper.class);
	private static final String FILE_SUFFIX = "pdf";
	private static final String ZUGFERD_FILEHANDLING_SYSCONFIGNAME = "ZUGFERD_FILE_HANDLING";
	
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
		String fileName = getDefaultFileName();

		MAttachment m_attachment = MAttachment.get(Env.getCtx(), MInvoice.Table_ID, invoice.getC_Invoice_ID(), invoice.getC_Invoice_UU(), null);
		if (m_attachment != null) {
			for (MAttachmentEntry entry : m_attachment.getEntries()) {
				if (entry.getName().equals(fileName)) {
					return entry.getFile();
				}
			}
		}

		return null;
	}
	
	private String getDefaultFileName() {
		MBPartner bPartner = MBPartner.get(Env.getCtx(), invoice.getC_BPartner_ID());
		String suffix = null;
		if (bPartner.get_ValueAsBoolean("BXS_IsXRechnung")) //If isXRechnung
			suffix = "xml";

		return getDefaultFileName(invoice, suffix);
	}
	
	public boolean isDefaultArchiveFileCreated() {
		String fileName = getDefaultFileName();
		StringBuilder sqlWhere = new StringBuilder(" AND AD_Table_ID=")
				.append(invoice.get_Table_ID())
				.append(" AND Record_ID=").append(invoice.getC_Invoice_ID())
				.append(" AND Name=").append(DB.TO_STRING(fileName));
		
		return MArchive.get(Env.getCtx(), sqlWhere.toString(), null).length > 0;
	}
	
	public void addArchivedFiles() {
		MArchive[] archives = getArchivesFromInvoice();
		
		if (archives != null && archives.length > 0) {
			for (MArchive archive : archives) {
				if (isValidFileExtension(archive.getName()))
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
	
	private boolean isValidFileExtension(String fileName) {
		return isPDFByExtension(fileName) || isXMLByExtension(fileName);
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
	
	public static boolean isXMLByExtension(String fileName) {
	    fileName = fileName.toLowerCase();
	    return fileName.endsWith(".xml");
	}
	
	
	public static String getDefaultFileName(MInvoice invoice) {
		return getDefaultFileName(invoice, null);
	}

	public static String getDefaultFileName(MInvoice invoice, String suffix) {
		if (Util.isEmpty(suffix)) 
			suffix = FILE_SUFFIX;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		return invoice.getDocumentNo() + "-" + sdf.format(invoice.getDateInvoiced()) + "." + suffix;
	}

	public List<File> getFiles() {
		return files;
	}

	public void clearFiles() {
		files.clear();
	}
	
	/**
	 * Defines if the created file should be attached or archived
	 * @return true if the file should be attached, false if the file should be archived
	 */
	public static boolean isFileForAttachment() {
		String fileHandling = MSysConfig.getValue(ZUGFERD_FILEHANDLING_SYSCONFIGNAME, "Attachment", Env.getAD_Client_ID(Env.getCtx())).toLowerCase();
		switch(fileHandling) {
		case "attachment":
			return true;
		case "archive":
			return false;
		default:
			throw new AdempiereException(ZUGFERD_FILEHANDLING_SYSCONFIGNAME + " system Configurator value not supported: " + fileHandling);
		}
	}
	
	/** 
	 * convert File data into Byte Data
	 * @param tempFile
	 * @return file in ByteData 
	 */
	public static byte[] getFileByteData(File tempFile) {
		try {
			return Files.readAllBytes(tempFile.toPath());
		} catch (IOException ioe) {
			log.log(Level.SEVERE, "Exception while reading file " + ioe);
			return null;
		}
	} 
}
