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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MAttachment;
import org.compiere.model.MInvoice;
import org.compiere.model.MPaymentTerm;
import org.compiere.model.MSysConfig;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Util;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentDiscountTerms;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentTerms;


public class ZUGFeRD extends SvrProcess {

	private ZugFerdGenerator zugFerdGenerator;
	String PRINTFORMATNAME = "ZUGFeRD";
	String FILE_SUFFIX="pdf";

	File printfile = new File("");
	Integer c_Bank_ID = 0;
	Integer c_BankAccount_ID = 0;
	String referenceNo = null;

	MInvoice m_invoice = null;
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
    	if (Util.isEmpty(referenceNo))
    		throw new AdempiereException(Msg.getMsg(Env.getLanguage(getCtx()), "Insert POReference !"));

    	zugFerdGenerator.setReferenceNo(referenceNo);

    	//Bank Details
    	zugFerdGenerator.setBank(c_Bank_ID);
    	zugFerdGenerator.setBankAccount(c_BankAccount_ID);
    	if(!zugFerdGenerator.isValidBankDetail())
    		throw new AdempiereException(Msg.getMsg(Env.getLanguage(getCtx()), "Check your Bankdetails !"));
   	
    	zugFerdGenerator.generateAndEmbeddXML(printfile);
    	
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

