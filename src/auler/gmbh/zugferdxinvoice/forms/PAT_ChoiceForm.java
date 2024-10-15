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
  * 2024
 */


package auler.gmbh.zugferdxinvoice.forms;


import org.adempiere.webui.LayoutUtils;
import org.adempiere.webui.adwindow.ADWindow;
import org.adempiere.webui.adwindow.AbstractADWindowContent;
import org.adempiere.webui.component.Column;
import org.adempiere.webui.component.Columns;
import org.adempiere.webui.component.ConfirmPanel;
import org.adempiere.webui.component.Grid;
import org.adempiere.webui.component.GridFactory;
import org.adempiere.webui.component.Label;
import org.adempiere.webui.component.ListItem;
import org.adempiere.webui.component.Listbox;
import org.adempiere.webui.component.Row;
import org.adempiere.webui.component.Rows;
import org.adempiere.webui.component.Textbox;
import org.adempiere.webui.component.Window;
import org.adempiere.webui.event.ValueChangeEvent;
import org.adempiere.webui.event.ValueChangeListener;
import org.adempiere.webui.event.WTableModelEvent;
import org.adempiere.webui.event.WTableModelListener;
import org.adempiere.webui.panel.ADForm;
import org.adempiere.webui.panel.IFormController;
import org.adempiere.webui.util.ZKUpdateUtil;
import org.compiere.model.GridTab;
import org.compiere.model.MBank;
import org.compiere.model.MBankAccount;
import org.compiere.model.MInvoice;
import org.compiere.model.MProcess;
import org.compiere.process.ProcessInfo;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.ServerProcessCtl;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.Vbox;

import auler.gmbh.zugferdxinvoice.utils.FileHelper;




public class PAT_ChoiceForm implements IFormController, EventListener<Event>, 
WTableModelListener, ValueChangeListener {

	private static final int CREATE_OPTION = 0;
	private static final int VIEW_OPTION = 1;
	
	private AbstractADWindowContent panel;
	private Window ZUGFeRDSelect = null;
	private String tTTButton = "PAT_ZUGFeRDXInvoice";
	private ConfirmPanel confirmPanel = new ConfirmPanel(true);
	private GridTab parentTab = null;

	private Listbox selectExportImport = new Listbox();
	private Listbox selectBankAccount = new Listbox();
	private Textbox referenceNumberEditor = new Textbox();
	
	private Row bankSelectorRow = new Row();
	private Row referenceNoRow = new Row();
	private Row fileSourceRow = new Row();
	private Listbox selectFileSource = new Listbox();
	
	private final String PROCESSNAME = "PAT_ZUGFeRD";
	
	private FileHelper fileHelper;
	
	public PAT_ChoiceForm(AbstractADWindowContent panel, ADWindow window, GridTab tab) {

		this.panel = panel;
		this.parentTab = tab;
		
		fileHelper = new FileHelper(MInvoice.get(parentTab.getRecord_ID()));

		this.show();

	}


	public void show() {

		ZUGFeRDSelect = new Window();

		ZKUpdateUtil.setWidth(ZUGFeRDSelect, "250px");
		ZUGFeRDSelect.setClosable(true);
		ZUGFeRDSelect.setBorder("normal");
		ZUGFeRDSelect.setStyle("position:absolute");
	
		Vbox vb = new Vbox();
		ZKUpdateUtil.setWidth(vb, "100%");
		ZUGFeRDSelect.appendChild(vb);
		ZUGFeRDSelect.setSclass("toolbar-popup-window");
		vb.setSclass("toolbar-popup-window-cnt");
		vb.setAlign("stretch");
		
		Grid grid = GridFactory.newGridLayout();
        
        Columns columns = new Columns();
        Column column = new Column();
        ZKUpdateUtil.setHflex(column, "min");
        columns.appendChild(column);
        column = new Column();
        ZKUpdateUtil.setHflex(column, "1");
        columns.appendChild(column);
        grid.appendChild(columns);
        
        Rows rows = new Rows();
		grid.appendChild(rows);
		Row row1 = new Row();
		rows.appendChild(row1);
		row1.appendChild(new Label(Msg.translate(Env.getCtx(), "Create/View")));
		selectExportImport.setMold("select");
		selectExportImport.getItems().clear();
		ZKUpdateUtil.setHflex(selectExportImport, "1");
		selectExportImport.appendItem(Msg.translate(Env.getCtx(), "Create ZUGFeRD"),0);	
		selectExportImport.appendItem(Msg.translate(Env.getCtx(), "View ZUGFeRD"),1);	
		row1.appendChild(selectExportImport);
		selectExportImport.addEventListener(Events.ON_SELECT, this);
		
		bankSelectorRow.appendChild(new Label(Msg.translate(Env.getCtx(), "Bank")));
		selectBankAccount.setMold("select");
		selectBankAccount.getItems().clear();
		ZKUpdateUtil.setHflex(selectBankAccount, "1");
		String SqlBankAccount = MBank.COLUMNNAME_C_Bank_ID+" IN (SELECT "+ MBank.COLUMNNAME_C_Bank_ID 
				+ " FROM " + MBank.Table_Name 
				+ " b WHERE b.isOwnBank='Y' AND b.AD_Client_ID="
				+ Env.getAD_Client_ID(Env.getCtx()) + ")";

		for (int bankAccountID : MBankAccount.getAllIDs(MBankAccount.Table_Name, SqlBankAccount, null)) {
			selectBankAccount.appendItem(MBankAccount.get(bankAccountID).getName(), MBankAccount.get(bankAccountID).getC_BankAccount_ID());
			if(MBankAccount.get(bankAccountID).isDefault())
				selectBankAccount.selectItem(selectBankAccount.getItemAtIndex(selectBankAccount.getItemCount()-1));
		}
		bankSelectorRow.appendCellChild(selectBankAccount);
		rows.appendChild(bankSelectorRow);
		
		referenceNoRow.appendChild(new Label(Msg.translate(Env.getCtx(), "PAT_ReferenceNo")));
		ZKUpdateUtil.setHflex(referenceNumberEditor, "1");
		referenceNumberEditor.setValue(getDefaultReferenceNo());
		referenceNoRow.appendCellChild(referenceNumberEditor);
		rows.appendChild(referenceNoRow);
		
		fileSourceRow.appendChild(new Label(Msg.translate(Env.getCtx(), "File")));
		selectFileSource.setMold("select");
		selectFileSource.getItems().clear();
		ZKUpdateUtil.setHflex(selectFileSource, "1");
		fileSourceRow.appendCellChild(selectFileSource);
		rows.appendChild(fileSourceRow);
		fileSourceRow.setVisible(false);
		
		vb.appendChild(grid);

		LayoutUtils.addSclass("dialog-footer", confirmPanel);
		confirmPanel.addActionListener(this);
		vb.appendChild(confirmPanel);
		
		LayoutUtils.openPopupWindow(panel.getToolbar().getButton(tTTButton), ZUGFeRDSelect, "after_start");		
		
	}

	private String getDefaultReferenceNo() {
		String defaultReferenceNo = null;
		int C_BPartner_ID = Env.getContextAsInt(Env.getCtx(), parentTab.getWindowNo(),"C_BPartner_ID");
		if (C_BPartner_ID > 0) {
			defaultReferenceNo = DB.getSQLValueString(null, "SELECT PAT_ReferenceNo FROM C_BPartner WHERE C_BPartner_ID=?", C_BPartner_ID);
		}

		return defaultReferenceNo;
	}

	@Override
	public void onEvent(Event event) throws Exception {

		if(event.getTarget().getId().equals(ConfirmPanel.A_CANCEL))
			ZUGFeRDSelect.onClose();

		
		else if(event.getTarget().getId().equals(ConfirmPanel.A_OK)) {
			
			ListItem item = selectExportImport.getSelectedItem();
			
			if(item.getValue().equals(CREATE_OPTION))
				createZUGFeRD();
			else
				viewZUGFeRD();
			
			ZUGFeRDSelect.onClose();
			
		} else if (Events.ON_SELECT.equals(event.getName())) { // Change Create/View value
			if (selectExportImport.getSelectedIndex() == CREATE_OPTION) {
				bankSelectorRow.setVisible(true);
				referenceNoRow.setVisible(true);
				fileSourceRow.setVisible(false);
			} else if (selectExportImport.getSelectedIndex() == VIEW_OPTION) {
				bankSelectorRow.setVisible(false);
				referenceNoRow.setVisible(false);
				populateFileSourceList();
				fileSourceRow.setVisible(true);
			}
		}
	}

	void createZUGFeRD() {
		
		if(IsZUGFeRDCreated()) {
			Messagebox.show("ZUGFeRD Invoice already created");
			
			return;
		}
		
		ProcessInfo pi = new ProcessInfo(Msg.translate(Env.getCtx(), "Create ZUGFeRD"), MProcess.getProcess_ID(PROCESSNAME, null));
		pi.setRecord_UU(parentTab.getRecord_UU());
		pi.setIsBatch(true);


		ListItem item = selectBankAccount.getSelectedItem();
		Integer bankAccount_ID = (Integer)item.getValue();
		String referenceNo = referenceNumberEditor.getValue();				
		
		ProcessInfoParameter parameterBank = new ProcessInfoParameter("C_Bank_ID", MBankAccount.get(bankAccount_ID).getC_Bank_ID(), null, null, null);
		ProcessInfoParameter parameterBankAccount = new ProcessInfoParameter("C_BankAccount_ID", MBankAccount.get(bankAccount_ID).getC_BankAccount_ID(), null, null, null);
		ProcessInfoParameter parameterReferenceNo = new ProcessInfoParameter("PAT_ReferenceNo", referenceNo, null, null, null);
		
		ProcessInfoParameter[]  paraZUGFeRD = {parameterBank,parameterBankAccount,parameterReferenceNo};
		
		pi.setParameter(paraZUGFeRD);
		pi.setRecord_ID(parentTab.getRecord_ID());

		ServerProcessCtl.process(pi, null);
		pi.getLogs();
		
		
		if(pi.getSummary() == null)
			Messagebox.show("ZUGFeRD Invoice created");
		else
			Messagebox.show(pi.getSummary());
		
		parentTab.dataRefresh();

		
	}

	
	boolean IsZUGFeRDCreated() {
		if (FileHelper.isFileForAttachment())
			return fileHelper.getDefaultAttachmentFile() != null;
		else 
			return fileHelper.isDefaultArchiveFileCreated();
	}
	
	
	void viewZUGFeRD() {
		
		if (selectFileSource.getSelectedItem() != null) {
			new PAT_Visualize_InvoiceX_Form(panel, parentTab, fileHelper, selectFileSource.getSelectedIndex());
			
		} else
			Messagebox.show("No valid ZUGFeRD Invoice found");

	}
	
	private void populateFileSourceList() {
		if (selectFileSource.getItems().isEmpty()) {
			if (fileHelper.getFiles().isEmpty()) {
				fileHelper.addAttachmentFiles();
				fileHelper.addArchivedFiles();
			}
			
			for (int i = 0; i < fileHelper.getFiles().size() ; i++) {
				selectFileSource.appendItem(fileHelper.getFiles().get(i).getName(), i);
			}
		}
	}
	
	@Override
	public void valueChange(ValueChangeEvent evt) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void tableChanged(WTableModelEvent event) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public ADForm getForm() {
		// TODO Auto-generated method stub
		return null;
	}

}

