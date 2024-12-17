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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;

import javax.xml.transform.TransformerException;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.webui.AdempiereWebUI;
import org.adempiere.webui.LayoutUtils;
import org.adempiere.webui.adwindow.AbstractADWindowContent;
import org.adempiere.webui.component.Borderlayout;
import org.adempiere.webui.component.ConfirmPanel;
import org.adempiere.webui.component.Listbox;
import org.adempiere.webui.util.ZKUpdateUtil;
import org.compiere.model.GridTab;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.compiere.util.Msg;
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
import org.zkoss.zul.Label;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.North;
import org.zkoss.zul.South;
import org.zkoss.zul.Window;

import auler.gmbh.zugferdxinvoice.utils.FileHelper;


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
	ZUGFeRDInvoiceImporter docimporter = null;
	ZUGFeRDVisualizer zvi = null;
	Invoice invoice= null;
	String xmlFilename = "/tmp/factur-x.xml";
    
	StringBuilder contenthn = new StringBuilder();
	StringBuilder contenthc = new StringBuilder();
	StringBuilder contentRows = new StringBuilder();
	
	private ConfirmPanel confirmPanel = new ConfirmPanel(true);
	private AbstractADWindowContent panel;
	//*******************************
	
	
	private Listbox selectAttachment = new Listbox();
	private FileHelper fileHelper;
	private int selectedFile = 0;
	
	
	/** Logger */
	public static CLogger log = CLogger.getCLogger(PAT_Visualize_InvoiceX_Form.class);

	public PAT_Visualize_InvoiceX_Form(AbstractADWindowContent panel, GridTab tab, FileHelper fileHelper, int selectedFile) {

		this.panel = panel;	
		this.fileHelper = fileHelper;
		this.selectedFile = selectedFile;
		
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
		
		populateFiles();
		try {
			setContent(fileHelper.getFiles().get(selectedFile));
		} catch (Exception e) {
			e.printStackTrace();
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

	
	/**
	 * Populate combobox with attachments/archived files
	 */
	private void populateFiles()
	{
		if (log.isLoggable(Level.CONFIG))
			log.config("");

		//	Set Combo
		for (int i = 0; i < fileHelper.getFiles().size() ; i++) {
			selectAttachment.appendItem(fileHelper.getFiles().get(i).getName(), i);
			if (i == selectedFile) 
				selectAttachment.setSelectedIndex(i);
		}
	} // populateFiles

	private void setContent(File docFile)  throws Exception {
		
		if (isXMLFile(docFile)) {
			openFile(docFile.getName());
		}else {
			docimporter = new ZUGFeRDInvoiceImporter(docFile.getPath());
			if (docimporter.canParse()) {
				File xmlfile = new File(xmlFilename);
				FileOutputStream out = new FileOutputStream(xmlfile);
				out.write(docimporter.getRawXML());
				out.close();
				openFile(xmlFilename);
			}
			else {
				Messagebox.show(Msg.translate(Env.getCtx(), "PAT_InvalidFile"));
			}
		}
	}
	
	private boolean isXMLFile(File file) {
		return FileHelper.isXMLByExtension(file.getName());
	}
	
	private void openFile(String xmlFileName) {
		zvi = new ZUGFeRDVisualizer();
		
		Language langCode = ZUGFeRDVisualizer.Language.EN;
		if(Env.getLocaleLanguage(Env.getCtx()).getLanguageCode().equals("de"))
			langCode = ZUGFeRDVisualizer.Language.DE;
		else if(Env.getLocaleLanguage(Env.getCtx()).getLanguageCode().equals("fr"))
			langCode = ZUGFeRDVisualizer.Language.FR;

		try {
			String xml = zvi.visualize(xmlFileName, langCode);
			htmlhc.setContent(xml);
			AMedia media = new AMedia(null, null, null, xml.getBytes());
			iframe.setContent(media);
		} catch (FileNotFoundException | UnsupportedEncodingException | TransformerException e) {
			e.printStackTrace();
			throw new AdempiereException("Cannot open the xml file: " + xmlFileName);
		}
	}
	
	StringBuilder cleanString(String str) {
		
		return new StringBuilder(str.replaceAll("null", ""));
		
	}


	@Override
	public void onEvent(Event event) throws Exception {
		
		if(event.getName().equals("onSelect")) {
			
			
			try {
				setContent(fileHelper.getFiles().get(selectAttachment.getSelectedIndex()));
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
