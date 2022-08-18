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
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MAttachment;
import org.compiere.model.MBank;
import org.compiere.model.MBankAccount;
import org.compiere.model.MClient;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MOrg;
import org.compiere.print.MPrintFormat;
import org.compiere.print.ReportEngine;
import org.compiere.process.ProcessInfo;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.ServerProcessCtl;
import org.compiere.process.SvrProcess;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Trx;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentDiscountTerms;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentTerms;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA1;

import org.mustangproject.*;


public class ZUGFeRD extends SvrProcess {

	String PRINTFORMATNAME = "ZUGFeRD";
	String FILE_SUFFIX="pdf";
	Integer ad_Process_ID = 0;
	File printfile = new File("");
	Integer c_Bank_ID = 0;
	Integer c_BankAccount_ID = 0;
	MInvoice m_invoice = null;
    Trx trx = null;
    CLogger log = CLogger.getCLogger(ZUGFeRD.class);
    
	List<ProcessInfoParameter>  paraInvoiceX = new ArrayList<ProcessInfoParameter>();;

	HashMap<String, Object> params = new HashMap<String, Object>();
	
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
		
		prepInvoice();
		
		createInvoice();
		
		return null;
	}
	
	
	// #2 Run printprocess
	private void prepInvoice() throws Exception {

    	m_invoice =  new MInvoice(getCtx(), getRecord_ID(), get_TrxName());
    	
		ReportEngine re = ReportEngine.get(Env.getCtx(), ReportEngine.INVOICE, getRecord_ID());
		try
		{
			MPrintFormat format = re.getPrintFormat();
			File pdfFile = null;
			if (format.getJasperProcess_ID() > 0)	
			{
				ProcessInfo pi = new ProcessInfo("", format.getJasperProcess_ID());
				pi.setRecord_ID(getRecord_ID());
				pi.setIsBatch(true);
				pi.setParameter(getParameter());
									
				ServerProcessCtl.process(pi, null);
				printfile = pi.getPDFReport();
			}
			else
			{

				pdfFile = File
						.createTempFile(
								m_invoice.getDocumentNo(), ".pdf");

				printfile = re.getPDF(pdfFile);

			}
			
			log.info(printfile.getName());
		
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, e.getLocalizedMessage(), e);
			return;
		}
		
    }	
	
	
	// #3 start creation of ZUGFeRD xml and pdf
    private void createInvoice() throws IOException {
    	
    	
    	if(!m_invoice.isPosted())
    		throw new AdempiereException(Msg.getMsg(Env.getLanguage(getCtx()), "Document not posted !"));
    	
    	//Leitweg-ID
    	if(m_invoice.getPOReference() == null)
    		throw new AdempiereException(Msg.getMsg(Env.getLanguage(getCtx()), "Insert POReference !"));
    	
    	//Bank Details
    	MBank bank = new MBank(Env.getCtx(), c_Bank_ID, null);
    	MBankAccount baccount = new MBankAccount(Env.getCtx(), c_BankAccount_ID, null);
    	if((baccount.getIBAN()==null)||(bank.getRoutingNo()==null)||(bank.getName()==null))
    		throw new AdempiereException(Msg.getMsg(Env.getLanguage(getCtx()), "Check your Bankdetails !"));
    	
    	
    	boolean isARC = (m_invoice.getC_DocType().getDocBaseType().equals("ARC"))?true:false;
    	
    	ZUGFeRDExporterFromA1 ze = new ZUGFeRDExporterFromA1();
    	
    	MClient client = new MClient(Env.getCtx(), Env.getAD_Client_ID(getCtx()), null);
    	
    	ze.setProducer(client.getName());
    	ze.setCreator(((m_invoice.getAD_User().getBPName()==null)?"":m_invoice.getAD_User().getBPName()));

    	ze.ignorePDFAErrors();
    	ze.load(printfile.getAbsolutePath());
    	
    	log.info(printfile.getAbsolutePath());
    	
    	//Invoice
    	Invoice invoice = new Invoice();
    	
    	patpaymentterms pt = new patpaymentterms(m_invoice);
    	
    	invoice.setPaymentTerms(pt);
    	
    	String sqlduedate = "select * from 	paymenttermduedate("
    			+ String.valueOf(m_invoice.getC_PaymentTerm_ID())+ ","
    			+ "'" + String.valueOf(m_invoice.getDateInvoiced()) + "')";
    	Timestamp duedate = DB.getSQLValueTSEx(m_invoice.get_TrxName(), sqlduedate);

    	invoice.setDueDate(duedate);
    	invoice.setPaymentTermDescription(m_invoice.getC_PaymentTerm().getName());
    	invoice.setIssueDate(m_invoice.getDateInvoiced());
    	invoice.setDeliveryDate(m_invoice.getDateInvoiced());
    	invoice.setNumber(m_invoice.getDocumentNo());
    	
    	MOrg org = new MOrg(Env.getCtx(), m_invoice.getAD_Org_ID(), null);
    	StringBuilder addressSender = new StringBuilder();
    	if(org.getInfo().getC_Location().getAddress1()!=null)
    		addressSender.append(org.getInfo().getC_Location().getAddress1());
    	if(org.getInfo().getC_Location().getAddress2()!=null)
    		addressSender.append(org.getInfo().getC_Location().getAddress2());
    	if(org.getInfo().getC_Location().getAddress3()!=null)
    		addressSender.append(org.getInfo().getC_Location().getAddress3());
    	if(org.getInfo().getC_Location().getAddress4()!=null)
    		addressSender.append(org.getInfo().getC_Location().getAddress4());

    	TradeParty tradePartySender = new TradeParty(client.getName(), 
    			addressSender.toString(), 
    			org.getInfo().getC_Location().getPostal(), 
    			org.getInfo().getC_Location().getCity(), 
    			org.getInfo().getC_Location().getC_Country().getCountryCode());
    	
    	tradePartySender.addVATID(org.getInfo().getTaxID());
    	    	
    	BankDetails bankd = new BankDetails(baccount.getIBAN(), bank.getRoutingNo());
    	bankd.setAccountName(bank.getName());

    	tradePartySender.addBankDetails(bankd);
    	
    	StringBuilder addressRecipient = new StringBuilder();
    	if(m_invoice.getC_BPartner_Location().getC_Location().getAddress1()!=null)
    		addressRecipient.append(m_invoice.getC_BPartner_Location().getC_Location().getAddress1());
    	if(m_invoice.getC_BPartner_Location().getC_Location().getAddress2()!=null)
    		addressRecipient.append(m_invoice.getC_BPartner_Location().getC_Location().getAddress2());
    	if(m_invoice.getC_BPartner_Location().getC_Location().getAddress3()!=null)
    		addressRecipient.append(m_invoice.getC_BPartner_Location().getC_Location().getAddress3());
    	if(m_invoice.getC_BPartner_Location().getC_Location().getAddress4()!=null)
    		addressRecipient.append(m_invoice.getC_BPartner_Location().getC_Location().getAddress4());
    	
    	TradeParty tradePartyRecipient = new TradeParty(
    			m_invoice.getC_BPartner().getName()
    			+ ((m_invoice.getC_BPartner().getName2()==null)?"":", "+ m_invoice.getC_BPartner().getName2()), 
    			addressRecipient.toString(),
    			(m_invoice.getC_BPartner_Location().getC_Location().getPostal()==null)?"":m_invoice.getC_BPartner_Location().getC_Location().getPostal(),
    			(m_invoice.getC_BPartner_Location().getC_Location().getCity()==null)?"":m_invoice.getC_BPartner_Location().getC_Location().getCity(),
    			(m_invoice.getC_BPartner_Location().getC_Location().getC_Country().getCountryCode()==null)?"":m_invoice.getC_BPartner_Location().getC_Location().getC_Country().getCountryCode()
    			);
    	tradePartyRecipient.addVATID(m_invoice.getC_BPartner().getTaxID());
    	
    	Contact contact = new Contact((m_invoice.getAD_User().getName()==null)?"":m_invoice.getAD_User().getName(), 
    			(m_invoice.getAD_User().getPhone()==null)?"":m_invoice.getAD_User().getPhone(),
    			(m_invoice.getAD_User().getEMail()==null)?"":m_invoice.getAD_User().getEMail());
    	tradePartyRecipient.setContact(contact);

    	invoice.setSender(tradePartySender);
    	invoice.setRecipient(tradePartyRecipient);
    	
    	//Leitweg-ID
    	invoice.setReferenceNumber(m_invoice.getPOReference());

//    	invoice.setBuyerOrderReferencedDocumentID("");
//    	invoice.setBuyerOrderReferencedDocumentIssueDateTime(""); 
    	
    	
		for (MInvoiceLine invoiceLine : m_invoice.getLines()) {

			Item item = new Item();
			
			String sql = "select x12de355 from c_uom where c_uom_id="
						+ String.valueOf(invoiceLine.getC_UOM_ID());
			
			String uom = DB.getSQLValueString(invoiceLine.get_TrxName(), sql);
			
			
			if ((invoiceLine.isDescription())||((invoiceLine.getM_Product() != null)&&(invoiceLine.getCharge() != null))) {

				Product product = new Product();
				product.setName("Descriptionline");
				product.setDescription((invoiceLine.getDescription()==null?"":invoiceLine.getDescription()));
				product.setVATPercent(Env.ZERO);
				product.setUnit("C62");
				
				item.setProduct(product);
				item.setQuantity(Env.ZERO);
				item.setPrice(Env.ZERO);
				item.setTax(Env.ZERO);
				item.setLineTotalAmount(Env.ZERO);
				
				invoice.addItem(item);

				
			} else if (invoiceLine.getM_Product_ID() > 0) {
				
				Product product = new Product();
				product.setName(invoiceLine.getM_Product().getValue());
				product.setDescription((invoiceLine.getDescription()==null?"":invoiceLine.getDescription()));
				product.setVATPercent(invoiceLine.getC_Tax().getRate());
				product.setUnit(uom);
				product.setSellerAssignedID(invoiceLine.getM_Product().getValue());

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
				product.setName(invoiceLine.getC_Charge().getName());
				product.setDescription((invoiceLine.getDescription()==null?"":invoiceLine.getDescription()));
				product.setVATPercent(invoiceLine.getC_Tax().getRate());
				product.setUnit("C62");

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
		
		getProcessInfo().getProcessUI().download(printdstfile);

    }
    
    
    static class patpaymentterms implements IZUGFeRDPaymentTerms {
    	
    	MInvoice inv = null;
     	Timestamp duedate = null;
     	patdiscountterms pt = null;
     	
    	patpaymentterms(MInvoice minv) {
    		
    		inv=minv;
    		
    		if(inv != null) {
    			
    			if(inv.getC_PaymentTerm().getDiscount().compareTo(Env.ZERO)>0)
    				pt = new patdiscountterms(inv);
    			else {
    				String sqlduedate = "select * from 	paymenttermduedate(" + String.valueOf(inv.getC_PaymentTerm_ID())+ ","
    						+ "'" + String.valueOf(inv.getDateInvoiced()) + "')";  
    				duedate = DB.getSQLValueTSEx(inv.get_TrxName(), sqlduedate);
    			}
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
			
			return inv.getC_PaymentTerm().getDescription();
			
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
			
    		if(inv != null) 
    			return inv.getC_PaymentTerm().getDiscount();
    		else
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

