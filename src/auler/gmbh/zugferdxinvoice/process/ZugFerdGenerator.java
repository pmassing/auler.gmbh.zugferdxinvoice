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
package auler.gmbh.zugferdxinvoice.process;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Timestamp;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MArchive;
import org.compiere.model.MAttachment;
import org.compiere.model.MAttachmentEntry;
import org.compiere.model.MBPartner;
import org.compiere.model.MBPartnerLocation;
import org.compiere.model.MBank;
import org.compiere.model.MBankAccount;
import org.compiere.model.MCharge;
import org.compiere.model.MClient;
import org.compiere.model.MCountry;
import org.compiere.model.MDocType;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MLocation;
import org.compiere.model.MOrder;
import org.compiere.model.MOrg;
import org.compiere.model.MOrgInfo;
import org.compiere.model.MPaymentTerm;
import org.compiere.model.MProduct;
import org.compiere.model.MProject;
import org.compiere.model.MSysConfig;
import org.compiere.model.MTax;
import org.compiere.model.MUOM;
import org.compiere.model.MUser;
import org.compiere.model.PrintInfo;
import org.compiere.print.MPrintFormat;
import org.compiere.print.ReportEngine;
import org.compiere.process.ProcessInfo;
import org.compiere.process.ServerProcessCtl;
import org.compiere.tools.FileUtil;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Language;
import org.compiere.util.Util;
import org.mustangproject.BankDetails;
import org.mustangproject.Contact;
import org.mustangproject.FileAttachment;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRD2PullProvider;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA1;

import auler.gmbh.zugferdxinvoice.process.ZUGFeRD.patpaymentterms;
import auler.gmbh.zugferdxinvoice.utils.FileHelper;

public class ZugFerdGenerator {

	private CLogger log = CLogger.getCLogger(ZugFerdGenerator.class);

	private static final String CD_UOM="C62";

	private MInvoice invoice;
	private MBank bank;
	private MBankAccount bankAccount;
	private String referenceNo;
	
	private String invoiceProducer;
	private String invoiceAuthor;
	private String language = Language.getBaseAD_Language();

	public ZugFerdGenerator(MInvoice invoice) {
		this.invoice = invoice;
	}

	public File generateInvoicePDF() {
		ReportEngine re = ReportEngine.get(Env.getCtx(), ReportEngine.INVOICE, invoice.getC_Invoice_ID());
		MPrintFormat format = re.getPrintFormat();
		File pdfFile = null;
		if (format.getJasperProcess_ID() > 0) {
			ProcessInfo pi = new ProcessInfo("", format.getJasperProcess_ID());
			pi.setRecord_ID(invoice.getC_Invoice_ID());
			pi.setIsBatch(true);	

			ServerProcessCtl.process(pi, null);
			pdfFile = pi.getPDFReport();
		} else {
			pdfFile = re.getPDF(pdfFile);
		}

		log.info("PDF Created: " + pdfFile.getName());

		return pdfFile;
	}

	public void setBank(int C_Bank_ID) {
		bank = MBank.get(C_Bank_ID);
	}

	public void setBankAccount(int C_BankAccount_ID) {
		bankAccount = MBankAccount.get(C_BankAccount_ID);
		if (bankAccount != null && bank == null)
			setBank(bankAccount.getC_Bank_ID());
	}
	
	public void setReferenceNo(String referenceNo) {
		this.referenceNo = referenceNo;
	}
	
	public void setLanguage(String language) {
		this.language = language;
	}

	public String getReferenceNo() {
		if (Util.isEmpty(referenceNo)) {
			MBPartner bp = MBPartner.get(Env.getCtx(), invoice.getC_BPartner_ID());
			referenceNo = bp.get_ValueAsString("PAT_ReferenceNo");
		}
		return referenceNo;
	}

	public boolean isValidBankDetail() {
		return bank != null && bankAccount != null && 
				bankAccount.getIBAN() != null && 
				bank.getSwiftCode() != null && bank.getName() != null;
	}
	
	public void generateAndSaveXRechnungXML() throws IOException {
		File file = generateXRechnungXML();
		saveFileInSystem(file);
	}
	
	public File generateXRechnungXML() throws IOException {
		if (Util.isEmpty(getReferenceNo())) {
			throw new AdempiereException("Leitweg-ID is mandatory for XRechnung");
		}
		Invoice zugFerdInvoice = generateZUGFeRDInvoice();

		ZUGFeRD2PullProvider zf2p = new ZUGFeRD2PullProvider();
		zf2p.setProfile(Profiles.getByName("XRechnung"));
		zf2p.generateXML(zugFerdInvoice);
		String theXML = new String(zf2p.getXML());
		String fileName = FileHelper.getDefaultFileName(invoice, "xml");
		File outputFile = FileUtil.createFile(fileName);
		BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
		try {
			writer.write(theXML);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			writer.close();
		}
		
		return outputFile;
	}

	public void generateAndEmbeddXML(File pdfFile) throws IOException {
		generateZugFerdXML(pdfFile);
		savePdfFile(pdfFile);
	}

	public void generateZugFerdXML(File pdfFile) throws IOException {
		ZUGFeRDExporterFromA1 ze = new ZUGFeRDExporterFromA1();
		ze.setProducer(getInvoiceProducer());
		ze.setCreator(invoiceAuthor);

		ze.ignorePDFAErrors();
		loadFile(ze, pdfFile);

		Invoice zugFerdInvoice = generateZUGFeRDInvoice();
		exportFile(ze, zugFerdInvoice, pdfFile);
	}
	
	public Invoice generateZUGFeRDInvoice() {
		Invoice zugFerdInvoice = new Invoice();
		generateHeader(zugFerdInvoice);
		generateLines(zugFerdInvoice);
		embedFiles(zugFerdInvoice);
		
		return zugFerdInvoice; 
	}
	
	public void loadFile(ZUGFeRDExporterFromA1 ze, File pdfFile) {
		try {
			ze.load(pdfFile.getAbsolutePath());
		} catch (IOException e) {
			throw new AdempiereException("Error while loading file: " + pdfFile.getAbsolutePath());
		}
	}

	private void generateHeader(Invoice zugFerdInvoice) {
		MClient client = MClient.get(Env.getAD_Client_ID(Env.getCtx()));
		MUser invoiceUser = MUser.get(invoice.getAD_User_ID());

		patpaymentterms pt = new patpaymentterms(invoice, language);
//		zugFerdInvoice.setPaymentTerms(pt);

		Timestamp duedate = (Timestamp) pt.getDueDate();
		zugFerdInvoice.setDueDate(duedate);

		zugFerdInvoice.setPaymentTermDescription(pt.getDescription());
		zugFerdInvoice.setIssueDate(invoice.getDateInvoiced());
		if(invoice.getC_Project_ID()>0) {
			MProject project = new MProject(Env.getCtx(), invoice.getC_Project_ID(), invoice.get_TrxName());		
			zugFerdInvoice.setSpecifiedProcuringProjectID(project.getValue());
			zugFerdInvoice.setSpecifiedProcuringProjectName(project.getName());
		}
		if(invoice.getC_Order_ID()>0) {
			MOrder order = new MOrder(Env.getCtx(), invoice.getC_Order_ID(), invoice.get_TrxName());
			zugFerdInvoice.setSellerOrderReferencedDocumentID(order.getDocumentNo());
		}
		if(isCollectiveInvoice(invoice)){
			zugFerdInvoice.setDetailedDeliveryPeriod(getMovementDateFirst(invoice), getMovementDateLast(invoice));
		} else if(getMovementDateLast(invoice)!= null){
			zugFerdInvoice.setDeliveryDate(getMovementDateLast(invoice));
		} else {
			zugFerdInvoice.setDeliveryDate(invoice.getDateInvoiced());
		}
		zugFerdInvoice.setNumber(invoice.getDocumentNo());

		MOrg org = new MOrg(Env.getCtx(), invoice.getAD_Org_ID(), null);
		MOrgInfo orgInfo = MOrgInfo.get(org.getAD_Org_ID());
		MLocation orgLocation = MLocation.get(orgInfo.getC_Location_ID());
		String addressSender = generateAddressString(orgLocation);

		MCountry orgCountry = MCountry.get(orgLocation.getC_Country_ID());
		TradeParty tradePartySender = new TradeParty(client.getName(), 
				addressSender, 
				orgLocation.getPostal(), 
				orgLocation.getCity(), 
				orgCountry.getCountryCode());

		tradePartySender.addVATID(org.getInfo().getTaxID());

		Contact sellerContact = new Contact(org.getName(), 
				safeString(orgInfo.getPhone()),
				safeString(orgInfo.getEMail()));
		tradePartySender.setContact(sellerContact);
		tradePartySender.setEmail(safeString(orgInfo.getEMail()));

		BankDetails bankd = new BankDetails(bankAccount.getIBAN(), bank.getSwiftCode());
		bankd.setAccountName(bank.getName());

		tradePartySender.addBankDetails(bankd);

		MBPartnerLocation bpLocation = new MBPartnerLocation(Env.getCtx(), invoice.getC_BPartner_Location_ID(), null);
		MLocation location = bpLocation.getLocation(true);
		String addressRecipient = generateAddressString(location);

		MBPartner bp = MBPartner.get(Env.getCtx(), invoice.getC_BPartner_ID());
		MCountry bpCountry = MCountry.get(location.getC_Country_ID());
		TradeParty tradePartyRecipient = new TradeParty(
				bp.getName()
				+ safeString(bp.getName2()),
				addressRecipient,
				safeString(location.getPostal()),
				safeString(location.getCity()),
				safeString(bpCountry.getCountryCode()));
		tradePartyRecipient.addVATID(bp.getTaxID());

		Contact contact = new Contact(safeString(invoiceUser.getName()), 
				safeString(invoiceUser.getPhone()),
				safeString(invoiceUser.getEMail()));
		tradePartyRecipient.setContact(contact);
		tradePartyRecipient.setEmail(safeString(invoiceUser.getEMail()));

		zugFerdInvoice.setSender(tradePartySender);
		zugFerdInvoice.setRecipient(tradePartyRecipient);

		//Leitweg-ID
		zugFerdInvoice.setReferenceNumber(getReferenceNo());
		zugFerdInvoice.setBuyerOrderReferencedDocumentID(invoice.getPOReference());
	}
	
	private String generateAddressString(MLocation location) {
		StringBuilder addressSender = new StringBuilder();
		if (!Util.isEmpty(location.getAddress1()))
			addressSender.append(location.getAddress1());
		if (!Util.isEmpty(location.getAddress2()))
			appendWithSpace(addressSender, location.getAddress2());
		if (!Util.isEmpty(location.getAddress3()))
			appendWithSpace(addressSender, location.getAddress3());
		if (!Util.isEmpty(location.getAddress4()))
			appendWithSpace(addressSender, location.getAddress4());
		
		return addressSender.toString();
	}
	
	private void appendWithSpace(StringBuilder sb, String value) {
	    if (sb.length() > 0) {  // Add a space ONLY if there's already content
	        sb.append(" ");
	    }
	    sb.append(value);
	}

	private void generateLines(Invoice zugFerdInvoice) {
		MDocType docType = MDocType.get(invoice.getC_DocType_ID());
		boolean isARC = (docType.getDocBaseType().equals(MInvoice.DOCBASETYPE_ARCreditMemo));

		for (MInvoiceLine invoiceLine : invoice.getLines()) {
			Item item = new Item();

			MUOM unitOfMeasure = MUOM.get(invoiceLine.getC_UOM_ID());
			String uom = unitOfMeasure.getUNCEFACT();

			if (invoiceLine.isDescription() || (invoiceLine.getM_Product_ID() == 0 && invoiceLine.getC_Charge_ID() == 0)) {
				Product product = new Product();
				product.setName("Descriptionline");
				product.setDescription(safeString(invoiceLine.getDescription()));
				product.setVATPercent(Env.ZERO);
				product.setUnit(CD_UOM);

				item.setProduct(product);
				item.setQuantity(Env.ZERO);
				item.setPrice(Env.ZERO);
				item.setTax(Env.ZERO);
				item.setLineTotalAmount(Env.ZERO);

				zugFerdInvoice.addItem(item);
			} else if (invoiceLine.getM_Product_ID() > 0) {

				Product product = new Product();
				MProduct productLine = MProduct.get(invoiceLine.getM_Product_ID());
				product.setName(productLine.get_Translation("Name", language, false, true));
				product.setDescription(safeString(invoiceLine.getDescription()));
				MTax tax = MTax.get(invoiceLine.getC_Tax_ID());
				product.setVATPercent(tax.getRate());
				product.setUnit(uom);
				product.setSellerAssignedID(productLine.getValue());

				item.setProduct(product);
				if (isARC)
					item.setQuantity(invoiceLine.getQtyInvoiced().negate());
				else
					item.setQuantity(invoiceLine.getQtyInvoiced());

				item.setPrice(invoiceLine.getPriceActual());
				item.setTax(invoiceLine.getTaxAmt());
				item.setLineTotalAmount(invoiceLine.getLineTotalAmt());
				if(isCollectiveInvoice(invoice) && (invoiceLine.getM_InOutLine_ID()>0)){
					item.setDetailedDeliveryPeriod(getMovementDate(invoiceLine), getMovementDate(invoiceLine));
				}
				zugFerdInvoice.addItem(item);
			} else if (invoiceLine.getC_Charge_ID() > 0) {

				Product product = new Product();
				MCharge charge = MCharge.get(invoiceLine.getC_Charge_ID());
				product.setName(charge.get_Translation("Name", language, false, true));
				product.setDescription(safeString(invoiceLine.getDescription()));
				MTax tax = MTax.get(invoiceLine.getC_Tax_ID());
				product.setVATPercent(tax.getRate());
				product.setUnit(CD_UOM);

				item.setProduct(product);
				if (isARC)
					item.setQuantity(invoiceLine.getQtyInvoiced().negate());
				else
					item.setQuantity(invoiceLine.getQtyInvoiced());
				item.setPrice(invoiceLine.getPriceActual());
				item.setTax(invoiceLine.getTaxAmt());
				item.setLineTotalAmount(invoiceLine.getLineTotalAmt());
				if(isCollectiveInvoice(invoice) && (invoiceLine.getM_InOutLine_ID()>0)){
					item.setDetailedDeliveryPeriod(getMovementDate(invoiceLine), getMovementDate(invoiceLine));
				}
				zugFerdInvoice.addItem(item);
			}
		}

	}
	
	
	/**
	 * Embed Files to factur-x.xml from attachments of doctype(target)
	 * of the current invoice. 
	 * Only filetype pdf is embedded
	 * 
	 * @param ze
	 * @param zugFerdInvoice
	 * 
	 */
	private void embedFiles(Invoice zugFerdInvoice) {
		
		MAttachment atm = new MAttachment(Env.getCtx(), MDocType.Table_ID, invoice.getC_DocTypeTarget_ID(), invoice.getC_DocTypeTarget().getC_DocType_UU(),  null);
		
		for ( MAttachmentEntry entry : atm.getEntries()) {
			if (entry.isPDF()) {
				FileAttachment fa = new FileAttachment(entry.getName(), entry.getContentType(), "", entry.getData());
				zugFerdInvoice.embedFileInXML(fa);
			}
		}

	}

	
	private void exportFile(ZUGFeRDExporterFromA1 ze, Invoice zugFerdInvoice, File pdfFile) {
		try {
			ze.setTransaction(zugFerdInvoice);
			ze.export(pdfFile.getAbsolutePath());
			ze.close();
		} catch (IOException e) {
			throw new AdempiereException(e.getMessage());
		}
	}
	
	public void savePdfFile(File pdfFile) {
		File printdstfile = new File(pdfFile.getParent()
				+ "/" + FileHelper.getDefaultFileName(invoice));
		pdfFile.renameTo(printdstfile);

		saveFileInSystem(printdstfile);
	}
	
	private void saveFileInSystem(File file) {
		if (FileHelper.isFileForAttachment()) {
			attachFile(file);
		} else {
			archiveFile(file);
		}
	}
	
	private void attachFile(File file) {
		MAttachment atmt = invoice.createAttachment();
		atmt.addEntry(file);
		atmt.saveEx();
	}
	
	private void archiveFile(File file) {
		PrintInfo printInfo = new PrintInfo(file.getName(), invoice.get_Table_ID(), invoice.get_ID(), invoice.getC_BPartner_ID());
		byte[] data = FileHelper.getFileByteData(file);
		MArchive archive = new MArchive(Env.getCtx(), printInfo, null);
		archive.setBinaryData(data);
		archive.save();
	}
	
	public String getInvoiceProducer() {
		if (Util.isEmpty(invoiceProducer))
			invoiceProducer = MSysConfig.getValue("ZUGFERD_PRODUCER", "iDempiere", Env.getAD_Client_ID(Env.getCtx()));
		return invoiceProducer;
	}

	public void setInvoiceProducer(String invoiceProducer) {
		this.invoiceProducer = invoiceProducer;
	}


	public void setInvoiceAuthor(String invoiceAuthor) {
		this.invoiceAuthor = invoiceAuthor;
	}
	
	/**
	 * Returns the string value of the input or empty string if null
	 * @param value the value to convert
	 * @return the string value or empty string if null
	 */
	private String safeString(String value) {
	    return Util.isEmpty(value, true) ? "" : value;
	}

	private Boolean isCollectiveInvoice(MInvoice invoice) {
		
		String sql ="SELECT COUNT(MOVEMENTDATE) FROM\n"
				+ "(SELECT\n"
				+ "	IO.MOVEMENTDATE\n"
				+ "FROM\n"
				+ "	M_INOUTLINE IOL\n"
				+ "	  JOIN C_INVOICELINE IL ON IL.M_INOUTLINE_ID = IOL.M_INOUTLINE_ID\n"
				+ "	  JOIN M_INOUT IO ON IO.M_INOUT_ID = IOL.M_INOUT_ID\n"
				+ "WHERE\n"
				+ " IL.C_INVOICE_ID = ?\n"
				+ "GROUP BY\n"
				+ "	IO.MOVEMENTDATE\n)";
				
		Integer ret = DB.getSQLValue(null, sql, invoice.getC_Invoice_ID());
		
		return (ret>1)?true:false;
		
	}

	private Timestamp getMovementDateFirst(MInvoice invoice) {
		
		String sql ="SELECT\n"
				+ "	MIN(IO.MOVEMENTDATE)\n"
				+ "FROM\n"
				+ "	M_INOUTLINE IOL\n"
				+ "	JOIN C_INVOICELINE IL ON IL.M_INOUTLINE_ID = IOL.M_INOUTLINE_ID\n"
				+ "	JOIN M_INOUT IO ON IO.M_INOUT_ID = IOL.M_INOUT_ID\n"
				+ "WHERE\n"
				+ "	IL.C_INVOICE_ID = ?\n"
				+ "GROUP BY\n"
				+ "	IO.MOVEMENTDATE";
		
		Timestamp MovementDateFirst = DB.getSQLValueTS(null, sql, invoice.getC_Invoice_ID());
		
		return MovementDateFirst;
	}

	private Timestamp getMovementDateLast(MInvoice invoice) {

		String sql ="SELECT\n"
				+ "	MAX(IO.MOVEMENTDATE)\n"
				+ "FROM\n"
				+ "	M_INOUTLINE IOL\n"
				+ "	JOIN C_INVOICELINE IL ON IL.M_INOUTLINE_ID = IOL.M_INOUTLINE_ID\n"
				+ "	JOIN M_INOUT IO ON IO.M_INOUT_ID = IOL.M_INOUT_ID\n"
				+ "WHERE\n"
				+ "	IL.C_INVOICE_ID = ?\n"
				+ "GROUP BY\n"
				+ "	IO.MOVEMENTDATE";
		
		Timestamp MovementDateLast = DB.getSQLValueTS(null, sql, invoice.getC_Invoice_ID());
		
		return MovementDateLast;
	}
	
	private Timestamp getMovementDate(MInvoiceLine line) {

		String sql ="SELECT\n"
				+ "	IO.MOVEMENTDATE\n"
				+ "FROM\n"
				+ "	M_INOUTLINE IOL\n"
				+ "	JOIN C_INVOICELINE IL ON IL.M_INOUTLINE_ID = IOL.M_INOUTLINE_ID\n"
				+ "	JOIN M_INOUT IO ON IO.M_INOUT_ID = IOL.M_INOUT_ID\n"
				+ "WHERE\n"
				+ "	IL.C_INVOICELINE_ID = ?";
		
		Timestamp MovementDateLast = DB.getSQLValueTS(null, sql, line.getC_InvoiceLine_ID());
		
		return MovementDateLast;
	}

}
