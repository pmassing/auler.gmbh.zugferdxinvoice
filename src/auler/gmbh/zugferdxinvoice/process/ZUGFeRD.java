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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MBPartner;
import org.compiere.model.MInvoice;
import org.compiere.model.MPaymentTerm;
import org.compiere.model.MSysConfig;
import org.compiere.model.MSystem;
import org.compiere.model.MUser;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Util;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentDiscountTerms;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentTerms;


public class ZUGFeRD extends SvrProcess {

	private ZugFerdGenerator zugFerdGenerator;
	String PRINTFORMATNAME = "ZUGFeRD";

	File printfile = new File("");
	Integer c_Bank_ID = 0;
	Integer c_BankAccount_ID = 0;
	String referenceNo = null;

	MInvoice m_invoice = null;
	boolean isXRechnung = false;
    CLogger log = CLogger.getCLogger(ZUGFeRD.class);
    
	List<ProcessInfoParameter>  paraInvoiceX = new ArrayList<ProcessInfoParameter>();;

	HashMap<String, Object> params = new HashMap<String, Object>();

	final static String sqlduedate = "SELECT PaymentTermDueDate(?,?) FROM Dual";

	@Override
	protected void prepare() {
		
		for (ProcessInfoParameter para : getParameter()) {
			
			if(para.getParameterName().equals("C_Bank_ID"))
				c_Bank_ID = para.getParameterAsInt();
			else if(para.getParameterName().equals("C_BankAccount_ID"))
				c_BankAccount_ID = para.getParameterAsInt();
			else if (para.getParameterName().equals("PAT_ReferenceNo"))
				referenceNo = para.getParameterAsString();

		}
		
	}

	@Override
	protected String doIt() throws Exception {
		m_invoice = MInvoice.get(getRecord_ID());
		zugFerdGenerator = new ZugFerdGenerator(m_invoice);
		
		setIsXRechnungBasedOnBusinessPartner();
		if (!isXRechnung)
			prepInvoice();

		createInvoice();
		
		return null;
	}
	
	private void setIsXRechnungBasedOnBusinessPartner() {
		MBPartner businessPartner = MBPartner.get(getCtx(), m_invoice.getC_BPartner_ID());
		isXRechnung = businessPartner.get_ValueAsBoolean("BXS_IsXRechnung");
	}
	
	// #2 Run printprocess
	private void prepInvoice() throws Exception {
		printfile = zugFerdGenerator.generateInvoicePDF();
    }	
	
	
	// #3 start creation of ZUGFeRD xml and pdf
    private void createInvoice() throws IOException {

    	boolean errorIfNotPosted = MSysConfig.getBooleanValue("ZUGFERD_ERROR_IF_NOT_POSTED", false, m_invoice.getAD_Client_ID(), m_invoice.getAD_Org_ID());
    	if(errorIfNotPosted && !m_invoice.isPosted())
    		throw new AdempiereException("@PAT_DocumentNotPosted@");
    	
    	//Leitweg-ID
    	zugFerdGenerator.setReferenceNo(referenceNo);
    	boolean isReferenceMandatory = MSysConfig.getBooleanValue("ZUGFERD_MANDATORY_REFERENCENO", true, m_invoice.getAD_Client_ID(), m_invoice.getAD_Org_ID());
    	if (isReferenceMandatory && Util.isEmpty(zugFerdGenerator.getReferenceNo()))
    		throw new AdempiereException("@FillMandatory@ @PAT_ReferenceNo@");

    	//Metadata values
    	zugFerdGenerator.setInvoiceProducer(MSystem.get(Env.getCtx()).getName());
		MUser invoiceUser = MUser.get(m_invoice.getCreatedBy());
		zugFerdGenerator.setInvoiceAuthor(((invoiceUser.getName() == null) ? "" : invoiceUser.getName()));
		
    	//Bank Details
    	zugFerdGenerator.setBank(c_Bank_ID);
    	zugFerdGenerator.setBankAccount(c_BankAccount_ID);
    	if(!zugFerdGenerator.isValidBankDetail())
    		throw new AdempiereException("@PAT_InvalidBank@");
   	
    	if (!isXRechnung)
    		zugFerdGenerator.generateAndEmbeddXML(printfile);
    	else
    		zugFerdGenerator.generateAndSaveXRechnungXML();
    }
    
    
    static class patpaymentterms implements IZUGFeRDPaymentTerms {
    	
    	MInvoice inv = null;
     	Timestamp duedate = null;
     	patdiscountterms pt = null;
     	String language;
     	
    	patpaymentterms(MInvoice minv, String language) {
    		
    		inv=minv;
    		this.language = language;
    		
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
	    	boolean usePaymentTermName = MSysConfig.getBooleanValue("ZUGFERD_USE_PAYMENT_TERM_NAME", false, Env.getAD_Client_ID(Env.getCtx()));
	    	
	    	String field = usePaymentTermName ? "Name" : "Description";
	    	String description = paymentTerm.get_Translation(field, language, false, true);
	    	
	    	return Optional.ofNullable(description).orElse("");
		}
		
		@Override
		public Date getDueDate() {
			return duedate;
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

