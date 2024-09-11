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


import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

import org.adempiere.webui.AdempiereWebUI;
import org.adempiere.webui.LayoutUtils;
import org.adempiere.webui.adwindow.AbstractADWindowContent;
import org.adempiere.webui.component.Borderlayout;
import org.adempiere.webui.component.ConfirmPanel;
import org.adempiere.webui.component.Listbox;
import org.adempiere.webui.util.ZKUpdateUtil;
import org.compiere.model.GridTab;
import org.compiere.model.MAttachment;
import org.compiere.model.MAttachmentEntry;
import org.compiere.model.MDocType;
import org.compiere.model.MInvoice;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Trx;
import org.mustangproject.Invoice;
import org.mustangproject.ZUGFeRD.ZUGFeRDInvoiceImporter;
import org.mustangproject.ZUGFeRD.ZUGFeRDVisualizer;
import org.mustangproject.ZUGFeRD.ZUGFeRDVisualizer.Language;
import org.zkoss.util.media.AMedia;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Center;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Html;
import org.zkoss.zul.Iframe;
import org.zkoss.zul.Window;

import org.zkoss.zul.Label;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.North;
import org.zkoss.zul.South;


public class PAT_Visualize_InvoiceX_Form extends Window implements EventListener<Event> {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3332263646678752907L;


	Borderlayout blayout = new Borderlayout();
	North north = new North();
	Center center = new Center();
	South south = new South();
	
	
	Window windowhn = new Window();
	Html htmlhn = new Html();
	Window windowhc = new Window();
	Html htmlhc = new Html();
	Iframe iframe = new Iframe();

	
	//*******************************
	private final String FILE_SUFFIX="pdf";
	Integer ad_Process_ID = 0;
	File printfile = new File("");
	Integer c_Bank_ID = 0;
	Integer c_BankAccount_ID = 0;
	MInvoice m_invoice = null;
	MAttachment atmt = null;
	MDocType docType = null;
	boolean isARC = false;
    Trx trx = null;
    
	ZUGFeRDInvoiceImporter docimporter = null;
	ZUGFeRDVisualizer zvi = null;
	Invoice invoice= null;
	String xmlFilename = "/tmp/factur-x.xml";
    
	List<ProcessInfoParameter>  paraInvoiceX = new ArrayList<ProcessInfoParameter>();;

	HashMap<String, Object> params = new HashMap<String, Object>();

	StringBuilder contenthn = new StringBuilder();
	StringBuilder contenthc = new StringBuilder();
	StringBuilder contentRows = new StringBuilder();
	
	private ConfirmPanel confirmPanel = new ConfirmPanel(true);

	
	private GridTab tab;
	private AbstractADWindowContent panel;
	
	/** Attachment				*/
	private MAttachment m_attachment = null;
	
	//*******************************
	
	
	private Listbox selectAttachment = new Listbox();
	
	
	/** Logger */
	public static CLogger log = CLogger.getCLogger(PAT_Visualize_InvoiceX_Form.class);

	public PAT_Visualize_InvoiceX_Form(AbstractADWindowContent panel, GridTab tab) {

		this.panel = panel;	
		this.tab = tab;
		
		init();

	}

	private void init() {
		
		this.setAttribute(AdempiereWebUI.WIDGET_INSTANCE_NAME, "InvoiceView");
		this.setStyle("position:absolute");
		this.setShadow(true);
		ZKUpdateUtil.setWidth(this, "80%");
		ZKUpdateUtil.setHeight(this, "80%");
		
		
		Borderlayout mainlayout = new Borderlayout();
		mainlayout.setStyle("width: 100%; height: 100%;");
		North north = new North();
		Center center = new Center();
		South south = new South();	
		
		//Parameter
		Hbox hbox = new Hbox();
		hbox.setStyle("width: 90%; height: 90%;");
		Label invoiceLabel = new Label(Msg.translate(Env.getCtx(), "Invoice"));
		hbox.appendChild(invoiceLabel);

		selectAttachment.setMold("select");
		selectAttachment.getItems().clear();
		selectAttachment.addEventListener(Events.ON_SELECT, this);

		m_attachment = new MAttachment (Env.getCtx(), MInvoice.Table_ID, tab.getRecord_ID(), tab.getRecord_UU(), null);
		loadAttachments();
		for (MAttachmentEntry entry : m_attachment.getEntries()) {
			if (entry.getName().equals(getFileName())) {
				try {
					selectAttachment.setSelectedItem(getItemByName(selectAttachment, getFileName()));
					setContent(entry);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		hbox.appendChild(selectAttachment);
		north.appendChild(hbox);
		
		// Content
		iframe.setStyle("width: 100%; height: 100%;");
		iframe.setHflex("true");
		iframe.setVflex("true");

		center.appendChild(iframe);
		

		LayoutUtils.addSclass("dialog-footer", confirmPanel);
		confirmPanel.addActionListener(this);
		south.appendChild(confirmPanel);

		mainlayout.appendChild(north);
		mainlayout.appendChild(center);
		mainlayout.appendChild(south);
		
		this.appendChild(mainlayout);
		
		LayoutUtils.addSclass("dialog-footer", confirmPanel);
		confirmPanel.addActionListener(this);

		LayoutUtils.openPopupWindow(panel.getToolbar().getParent(), this, "after_start");

	}


	private Listitem getItemByName(Listbox box, String Name) {
		
		for(Listitem item : box.getItems()) {
			if(item.getLabel().equals(Name)) {
				return item;
			}
		}
		
		return null;
	}
	
	/**
	 * Load Attachment items
	 */
	private void loadAttachments()
	{
		if (log.isLoggable(Level.CONFIG))
			log.config("");

		//	Set Combo
		for (MAttachmentEntry entry : m_attachment.getEntries()) {
			selectAttachment.appendItem(entry.getName(), entry.getIndex());
			if (entry.getName().equals(getFileName())){
			 selectAttachment.setSelectedItem(selectAttachment.getItemAtIndex(selectAttachment.getItemCount()));
			}
		}
		
		
	} // loadAttachment

	
	private String getFileName() {
		MInvoice m_invoice = new MInvoice(Env.getCtx(), tab.getRecord_ID(),null);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		return m_invoice.getDocumentNo() + "-" + sdf.format(m_invoice.getDateInvoiced())	+ "." + FILE_SUFFIX;
	}

	private void setContent(MAttachmentEntry entry)  throws Exception {
			
			File docFile = entry.getFile();

			docimporter = new ZUGFeRDInvoiceImporter(docFile.getPath());
			if(docimporter.canParse()) {
				
				zvi = new ZUGFeRDVisualizer();

				File xmlfile = new File(xmlFilename);
				FileOutputStream out = new FileOutputStream(xmlfile);
				out.write(docimporter.getRawXML());
				out.close();
				Language langCode = ZUGFeRDVisualizer.Language.EN;
				if(Env.getLocaleLanguage(Env.getCtx()).getLanguageCode().equals("de"))
					langCode = ZUGFeRDVisualizer.Language.DE;
				else if(Env.getLocaleLanguage(Env.getCtx()).getLanguageCode().equals("fr"))
					langCode = ZUGFeRDVisualizer.Language.FR;
				
				String xml = zvi.visualize(xmlFilename, langCode);

				htmlhc.setContent(xml);
				AMedia media = new AMedia(null, null, null, xml.getBytes());
				iframe.setContent(media);
			}
			else {
				Messagebox.show(Msg.translate(Env.getCtx(), "No valid ZUGFeRD-File"));
			}

	}	
	
	
	StringBuilder cleanString(String str) {
		
		return new StringBuilder(str.replaceAll("null", ""));
		
	}


	@Override
	public void onEvent(Event event) throws Exception {
		
		if(event.getName().equals("onSelect")) {
			
			
			try {
				setContent(m_attachment.getEntry(selectAttachment.getSelectedItem().getIndex()));
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		}

		
		if(event.getTarget().getId().equals(ConfirmPanel.A_CANCEL))
			this.onClose();
		
		else if(event.getTarget().getId().equals(ConfirmPanel.A_OK)) {
			this.onClose();
			
		}		

	}


}
