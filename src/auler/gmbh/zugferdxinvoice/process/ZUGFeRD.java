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
 *****************************************************************************/
 
 /**
  * @author Patric Maßing (Hans Auler GmbH)
  * 2022
 */

package auler.gmbh.zugferdxinvoice.process;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MAttachment;
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
import org.compiere.model.MOrg;
import org.compiere.model.MPaymentTerm;
import org.compiere.model.MProduct;
import org.compiere.model.MSysConfig;
import org.compiere.model.MTax;
import org.compiere.model.MUOM;
import org.compiere.model.MUser;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Trx;
import org.mustangproject.BankDetails;
import org.mustangproject.Contact;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentDiscountTerms;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentTerms;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA1;


public class ZUGFeRD extends SvrProcess {

	ZugFerdGenerator zugFerdGenerator;
	String PRINTFORMATNAME = "ZUGFeRD";
	String FILE_SUFFIX="pdf";
	String CD_UOM="C62";
	Integer ad_Process_ID = 0;
	File printfile = new File("");
	Integer c_Bank_ID = 0;
	Integer c_BankAccount_ID = 0;
	MInvoice m_invoice = null;
    Trx trx = null;
    CLogger log = CLogger.getCLogger(ZUGFeRD.class);
    
	List<ProcessInfoParameter>  paraInvoiceX = new ArrayList<ProcessInfoParameter>();;

	HashMap<String, Object> params = new HashMap<String, Object>();

	final static String sqlduedate = "SELECT PaymentTermDueDate(?,?) FROM Dual";

	@Override
	protected void prepare() {
		
		for (ProcessInfoParameter para : getParameter()) {
			
			if(para.getParameterName().equals("C_Bank_ID"))
				c_Bank_ID = para.getParameterAsInt();
			
			if(para.getParameterName().equals("C_BankAccount_ID"))
				c_BankAccount_ID = para.getParameterAsInt();

		}
		
	}

	@Override
	protected String doIt() throws Exception {
		m_invoice = MInvoice.get(getRecord_ID());
		zugFerdGenerator = new ZugFerdGenerator(m_invoice);
		prepInvoice();
		
		createInvoice();
		
		return null;
	}
	
	
	// #2 Run printprocess
	private void prepInvoice() throws Exception {
		printfile = zugFerdGenerator.generateInvoicePDF();
    }	
	
	
	// #3 start creation of ZUGFeRD xml and pdf
    private void createInvoice() throws IOException {

    	boolean errorIfNotPosted = MSysConfig.getBooleanValue("ZUGFERD_ERROR_IF_NOT_POSTED", false, m_invoice.getAD_Client_ID(), m_invoice.getAD_Org_ID());
    	if(errorIfNotPosted && !m_invoice.isPosted())
    		throw new AdempiereException(Msg.getMsg(Env.getLanguage(getCtx()), "Document not posted !"));
    	
    	//Leitweg-ID
    	if(m_invoice.getPOReference() == null)
    		throw new AdempiereException(Msg.getMsg(Env.getLanguage(getCtx()), "Insert POReference !"));
    	
    	//Bank Details
    	MBank bank = new MBank(Env.getCtx(), c_Bank_ID, null);
    	MBankAccount baccount = new MBankAccount(Env.getCtx(), c_BankAccount_ID, null);
    	if((baccount.getIBAN()==null)||(bank.getRoutingNo()==null)||(bank.getName()==null))
    		throw new AdempiereException(Msg.getMsg(Env.getLanguage(getCtx()), "Check your Bankdetails !"));
    	
    	MDocType docType = MDocType.get(m_invoice.getC_DocType_ID());
    	boolean isARC = (docType.getDocBaseType().equals(MInvoice.DOCBASETYPE_ARCreditMemo));
    	
    	ZUGFeRDExporterFromA1 ze = new ZUGFeRDExporterFromA1();
    	
    	MClient client = new MClient(Env.getCtx(), Env.getAD_Client_ID(getCtx()), null);
    	
    	ze.setProducer(client.getName());
    	MUser invoiceUser = MUser.get(m_invoice.getAD_User_ID());
    	ze.setCreator(((invoiceUser.getBPName()==null)?"":invoiceUser.getBPName()));

    	ze.ignorePDFAErrors();
    	ze.load(printfile.getAbsolutePath());
    	
    	log.info(printfile.getAbsolutePath());
    	
    	//Invoice
    	Invoice invoice = new Invoice();
    	
    	patpaymentterms pt = new patpaymentterms(m_invoice);
    	
    	invoice.setPaymentTerms(pt);
    	
    	Timestamp duedate = DB.getSQLValueTSEx(m_invoice.get_TrxName(), sqlduedate, m_invoice.getC_PaymentTerm_ID(), m_invoice.getDateInvoiced());

    	invoice.setDueDate(duedate);
    	MPaymentTerm paymentTerm = new MPaymentTerm(getCtx(), m_invoice.getC_PaymentTerm_ID(), get_TrxName());
    	invoice.setPaymentTermDescription(paymentTerm.getName());
    	invoice.setIssueDate(m_invoice.getDateInvoiced());
    	invoice.setDeliveryDate(m_invoice.getDateInvoiced());
    	invoice.setNumber(m_invoice.getDocumentNo());
    	
    	MOrg org = new MOrg(Env.getCtx(), m_invoice.getAD_Org_ID(), null);
    	StringBuilder addressSender = new StringBuilder();
    	MLocation orgLocation = MLocation.get(org.getInfo().getC_Location_ID());
    	if(orgLocation.getAddress1()!=null)
    		addressSender.append(orgLocation.getAddress1());
    	if(orgLocation.getAddress2()!=null)
    		addressSender.append(orgLocation.getAddress2());
    	if(orgLocation.getAddress3()!=null)
    		addressSender.append(orgLocation.getAddress3());
    	if(orgLocation.getAddress4()!=null)
    		addressSender.append(orgLocation.getAddress4());

    	MCountry orgCountry = MCountry.get(orgLocation.getC_Country_ID());
    	TradeParty tradePartySender = new TradeParty(client.getName(), 
    			addressSender.toString(), 
    			orgLocation.getPostal(), 
    			orgLocation.getCity(), 
    			orgCountry.getCountryCode());
    	
    	tradePartySender.addVATID(org.getInfo().getTaxID());
    	    	
    	BankDetails bankd = new BankDetails(baccount.getIBAN(), bank.getRoutingNo());
    	bankd.setAccountName(bank.getName());

    	tradePartySender.addBankDetails(bankd);
    	
    	StringBuilder addressRecipient = new StringBuilder();
    	
    	MBPartnerLocation bpLocation = new MBPartnerLocation(getCtx(), m_invoice.getC_BPartner_Location_ID(), null);
    	MLocation location = bpLocation.getLocation(true);
    	if(location.getAddress1()!=null)
    		addressRecipient.append(location.getAddress1());
    	if(location.getAddress2()!=null)
    		addressRecipient.append(location.getAddress2());
    	if(location.getAddress3()!=null)
    		addressRecipient.append(location.getAddress3());
    	if(location.getAddress4()!=null)
    		addressRecipient.append(location.getAddress4());

    	MBPartner bp = MBPartner.get(getCtx(), m_invoice.getC_BPartner_ID());
    	MCountry bpCountry = MCountry.get(location.getC_Country_ID());
    	TradeParty tradePartyRecipient = new TradeParty(
    			bp.getName()
    			+ ((bp.getName2()==null)?"":", "+ bp.getName2()), 
    			addressRecipient.toString(),
    			(location.getPostal()==null)?"":location.getPostal(),
    			(location.getCity()==null)?"":location.getCity(),
    			(bpCountry.getCountryCode()==null)?"":bpCountry.getCountryCode()
    			);
    	tradePartyRecipient.addVATID(bp.getTaxID());
    	
    	Contact contact = new Contact((invoiceUser.getName()==null)?"":invoiceUser.getName(), 
    			(invoiceUser.getPhone()==null)?"":invoiceUser.getPhone(),
    			(invoiceUser.getEMail()==null)?"":invoiceUser.getEMail());
    	tradePartyRecipient.setContact(contact);

    	invoice.setSender(tradePartySender);
    	invoice.setRecipient(tradePartyRecipient);
    	
    	//Leitweg-ID
    	invoice.setReferenceNumber(m_invoice.getPOReference());

//    	invoice.setBuyerOrderReferencedDocumentID("");
//    	invoice.setBuyerOrderReferencedDocumentIssueDateTime(""); 
    	
    	
		for (MInvoiceLine invoiceLine : m_invoice.getLines()) {

			Item item = new Item();

			MUOM unitOfMeasure = MUOM.get(invoiceLine.getC_UOM_ID());
			String uom = unitOfMeasure.getUNCEFACT();

			if (invoiceLine.isDescription() || (invoiceLine.getM_Product_ID() == 0 && invoiceLine.getC_Charge_ID() == 0)) {

				Product product = new Product();
				product.setName("Descriptionline");
				product.setDescription((invoiceLine.getDescription()==null?"":invoiceLine.getDescription()));
				product.setVATPercent(Env.ZERO);
				product.setUnit(CD_UOM);
				
				item.setProduct(product);
				item.setQuantity(Env.ZERO);
				item.setPrice(Env.ZERO);
				item.setTax(Env.ZERO);
				item.setLineTotalAmount(Env.ZERO);
				
				invoice.addItem(item);

				
			} else if (invoiceLine.getM_Product_ID() > 0) {
				
				Product product = new Product();
				MProduct productLine = MProduct.get(invoiceLine.getM_Product_ID());
				product.setName(productLine.getValue());
				product.setDescription((invoiceLine.getDescription()==null?"":invoiceLine.getDescription()));
				MTax tax = MTax.get(invoiceLine.getC_Tax_ID());
				product.setVATPercent(tax.getRate());
				product.setUnit(uom);
				product.setSellerAssignedID(productLine.getValue());

				item.setProduct(product);
				if(isARC)
					item.setQuantity(invoiceLine.getQtyInvoiced().negate().setScale(2, RoundingMode.HALF_UP));
				else
					item.setQuantity(invoiceLine.getQtyInvoiced().setScale(2, RoundingMode.HALF_UP));
				item.setPrice(invoiceLine.getPriceActual().setScale(2, RoundingMode.HALF_UP));
				item.setTax(invoiceLine.getTaxAmt().setScale(2, RoundingMode.HALF_UP));
				item.setLineTotalAmount(invoiceLine.getLineTotalAmt().setScale(2, RoundingMode.HALF_UP));
				invoice.addItem(item);
				
			} else if (invoiceLine.getC_Charge_ID() > 0) {
				
				Product product = new Product();
				MCharge charge = MCharge.get(invoiceLine.getC_Charge_ID());
				product.setName(charge.getName());
				product.setDescription((invoiceLine.getDescription()==null?"":invoiceLine.getDescription()));
				MTax tax = MTax.get(invoiceLine.getC_Tax_ID());
				product.setVATPercent(tax.getRate());
				product.setUnit(CD_UOM);

				item.setProduct(product);
				if(isARC)
					item.setQuantity(invoiceLine.getQtyInvoiced().negate().setScale(2, RoundingMode.HALF_UP));
				else
					item.setQuantity(invoiceLine.getQtyInvoiced().setScale(2, RoundingMode.HALF_UP));
				item.setPrice(invoiceLine.getPriceActual().setScale(2, RoundingMode.HALF_UP));
				item.setTax(invoiceLine.getTaxAmt().setScale(2, RoundingMode.HALF_UP));
				item.setLineTotalAmount(invoiceLine.getLineTotalAmt().setScale(2, RoundingMode.HALF_UP));
				invoice.addItem(item);
							
			}
			
		}
		
		ze.setTransaction(invoice);
		ze.export(printfile.getAbsolutePath());
		ze.close();

		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		File printdstfile = new File(printfile.getParent()
				+ "/" + m_invoice.getDocumentNo() +"-" 
				+ sdf.format(m_invoice.getDateInvoiced())
				+ "." + FILE_SUFFIX);
		printfile.renameTo(printdstfile);
		
		MAttachment atmt = m_invoice.createAttachment();
		atmt.addEntry(printdstfile);
		atmt.saveEx();

    }
    
    
    static class patpaymentterms implements IZUGFeRDPaymentTerms {
    	
    	MInvoice inv = null;
     	Timestamp duedate = null;
     	patdiscountterms pt = null;
     	
    	patpaymentterms(MInvoice minv) {
    		
    		inv=minv;
    		
    		if(inv != null) {
    	    	MPaymentTerm paymentTerm = new MPaymentTerm(inv.getCtx(), inv.getC_PaymentTerm_ID(), inv.get_TrxName());
    			if(paymentTerm.getDiscount().compareTo(Env.ZERO)>0)
    				pt = new patdiscountterms(inv);
    			else
    				duedate = DB.getSQLValueTSEx(inv.get_TrxName(), sqlduedate, inv.getC_PaymentTerm_ID(), inv.getDateInvoiced());
    		}
 		
    	}

		@Override
		public String getDescription() {

			// TODO: 
			// valid is to use description only
			
//			StringBuilder days = new StringBuilder(inv.getC_PaymentTerm().getDiscountDays());
//			StringBuilder dpercent = new StringBuilder(inv.getC_PaymentTerm().getDiscount().toString());
//			StringBuilder dbaseamt = new StringBuilder(inv.getGrandTotal().toString());
			
//			return "#SKONTO#TAGE=" + days + "#PROZENT=" + dpercent + "#BASISBETRAG=" + dbaseamt + "#";
			
	    	MPaymentTerm paymentTerm = new MPaymentTerm(inv.getCtx(), inv.getC_PaymentTerm_ID(), inv.get_TrxName());
			return paymentTerm.getDescription();
			
		}
		
		@Override
		public Date getDueDate() {
			return null;
		}

		@Override
		public IZUGFeRDPaymentDiscountTerms getDiscountTerms() {
			return null;
		};

    	
    }
    
    static class patdiscountterms implements IZUGFeRDPaymentDiscountTerms {
    	MInvoice inv = null;
    	 
    	Timestamp discountdate = null;
    	
    	patdiscountterms(MInvoice minv) {
    		
    	}    	

		@Override
		public BigDecimal getCalculationPercentage() {
			
    		if(inv != null) {
    	    	MPaymentTerm paymentTerm = new MPaymentTerm(inv.getCtx(), inv.getC_PaymentTerm_ID(), inv.get_TrxName());
    			return paymentTerm.getDiscount();
    		} else
    			return null;
		}

		@Override
		public Date getBaseDate() {
			
			if(inv!=null)
				
				return null;
			else
				return null;
		}

		@Override
		public int getBasePeriodMeasure() {

			return 0;
		}

		@Override
		public String getBasePeriodUnitCode() {

			return null;
		}

    	
    }
    
}

