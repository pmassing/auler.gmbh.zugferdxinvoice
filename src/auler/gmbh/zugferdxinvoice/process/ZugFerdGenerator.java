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

import java.io.File;

import org.compiere.model.MInvoice;
import org.compiere.print.MPrintFormat;
import org.compiere.print.ReportEngine;
import org.compiere.process.ProcessInfo;
import org.compiere.process.ServerProcessCtl;
import org.compiere.util.CLogger;
import org.compiere.util.Env;

public class ZugFerdGenerator {

	private CLogger log = CLogger.getCLogger(ZugFerdGenerator.class);

	private MInvoice invoice;

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

}
