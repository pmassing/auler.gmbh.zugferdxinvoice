package auler.gmbh.zugferdxinvoice.process;

import java.io.File;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MBPartner;
import org.compiere.model.MBankAccount;
import org.compiere.model.MInvoice;
import org.compiere.model.MSysConfig;
import org.compiere.model.MSystem;
import org.compiere.model.MUser;
import org.compiere.model.Query;
import org.compiere.process.ProcessInfo;
import org.compiere.process.SvrProcess;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Util;

/**
 * Calls ZUGFeRD generator <br>
 * Return File by calling {@link org.compiere.process.ProcessInfo.getExportFile}
 */
public class ZUGFeRDFileExport extends SvrProcess {
	
	
	@Override
	protected void prepare() {
		
	}

	@Override
	protected String doIt() throws Exception {
		
	
		MBankAccount bankAccount = new Query(getCtx(), MBankAccount.Table_Name, "isdefault = 'Y'", get_TrxName())
									.setClient_ID()
									.first();
		if (bankAccount == null)
			throw new AdempiereException(Msg.getMsg(getCtx(), "NoDefaultBankaccount"));
		MInvoice invoice = MInvoice.get(getRecord_ID());
		String referenceNo = invoice.getDocumentNo();
						
		ZugFerdGenerator zugFerdGenerator = new ZugFerdGenerator(invoice);
		
		MBPartner businessPartner = MBPartner.get(getCtx(), invoice.getC_BPartner_ID());
		boolean isXRechnung = businessPartner.get_ValueAsBoolean("BXS_IsXRechnung");
		boolean isUBLXRechnung = businessPartner.get_ValueAsBoolean("PAT_IsUBLXRechnung");
		String ublversion = businessPartner.get_ValueAsString("PAT_UBLVersion");
		
		File printfile = null;
		
		if (!isXRechnung) 
			printfile = zugFerdGenerator.generateInvoicePDF();
		
    	boolean errorIfNotPosted = MSysConfig.getBooleanValue("ZUGFERD_ERROR_IF_NOT_POSTED", false, invoice.getAD_Client_ID(), invoice.getAD_Org_ID());
    	if(errorIfNotPosted && !invoice.isPosted())
    		throw new AdempiereException("@PAT_DocumentNotPosted@");
    	
    	//Leitweg-ID
    	zugFerdGenerator.setReferenceNo(referenceNo);
    	boolean isReferenceMandatory = MSysConfig.getBooleanValue("ZUGFERD_MANDATORY_REFERENCENO", true, invoice.getAD_Client_ID(), invoice.getAD_Org_ID());
    	if (isReferenceMandatory && Util.isEmpty(zugFerdGenerator.getReferenceNo()))
    		throw new AdempiereException("@FillMandatory@ @PAT_ReferenceNo@");

    	//Metadata values
    	zugFerdGenerator.setInvoiceProducer(MSystem.get(Env.getCtx()).getName());
		MUser invoiceUser = MUser.get(invoice.getCreatedBy());
		zugFerdGenerator.setInvoiceAuthor(((invoiceUser.getName() == null) ? "" : invoiceUser.getName()));
		
    	//Bank Details
    	zugFerdGenerator.setBank(bankAccount.getC_Bank_ID());
    	zugFerdGenerator.setBankAccount(bankAccount.get_ID());
    	if(!zugFerdGenerator.isValidBankDetail())
    		throw new AdempiereException("@PAT_InvalidBank@");
   	
    	if (!isXRechnung) {
    		zugFerdGenerator.generateZugFerdXML(printfile);
    	} else if(isUBLXRechnung) {
    		zugFerdGenerator.generateXRechnungXML(true, ublversion);
    	} else {
    		printfile = zugFerdGenerator.generateXRechnungXML(false,"");
    	}
    	
    	ProcessInfo pi = getProcessInfo();
    	pi.setExportFile(printfile);
    	
    	return null;
    }
	

}
