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

package auler.gmbh.zugferdxinvoice;


import org.adempiere.webui.action.IAction;
import org.adempiere.webui.adwindow.ADWindow;
import org.adempiere.webui.adwindow.ADWindowContent;
import org.adempiere.webui.adwindow.AbstractADWindowContent;
import org.compiere.model.GridTab;
import org.compiere.util.CLogger;

import auler.gmbh.zugferdxinvoice.forms.PAT_ChoiceForm;



public class PAT_ToolbarAction implements IAction {

	private static CLogger log = CLogger.getCLogger(PAT_ToolbarAction.class);

	private AbstractADWindowContent panel;
	private GridTab tab = null;
	

	@Override
	public void execute(Object target) {
		


		ADWindow window = (ADWindow)target;
		ADWindowContent content = window.getADWindowContent();
		tab = content.getActiveGridTab();

		tab.getAD_Window_ID();
		
		
 		panel = content;
 		new PAT_ChoiceForm (panel, window, tab);
		

	}
	
}
