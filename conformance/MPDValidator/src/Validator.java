/** ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1
 *
 * The contents of this file are subject to the Mozilla  Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and  limitations under the
 * License.
 *
 * The Initial Developer of the Original Code is Alpen-Adria-Universitaet Klagenfurt, Austria
 * Portions created by the Initial Developer are Copyright (C) 2011
 * All Rights Reserved.
 *
 * The Initial Developer: Markus Waltl 
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK ***** */
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathExpressionException;

import org.iso.mpeg.dash.MPD;
import org.xml.sax.SAXException;


/**
 * Program for validating MPEG-DASH
 *	  
 * @author Markus Waltl <markus.waltl@itec.uni-klu.ac.at>
 */
 
 /** Return codes for the MPEG-DASH Validator
  * 1: You need at least JRE 1.6.0_17 to run this program!
  * 2: Provide file to parse
  * 3: Unable to get current path to executable
  * 4: MPD validation not successful - DASH is not valid!
  * 5: Malformed URL
  * 6: SAXException occurred
  * 7: IOException occurred
  * 8: ParserConfigurationException occurred
  * 9: XPathExpressionException occurred
  * 10: XLinkException occurred
  * 11: TransformerException occurred
  * 12: Unexpected error
  */
public class Validator {
	private static String system_os = "windows";
	
	/**
	 * Entry point
	 * @param args see printUsage()
	 */
	public static void main(String[] args) {
	    // check for minimum java version (this piece of software can only
	    // be run with JRE 1.6.0_17 or higher)
        String version = System.getProperty("java.version");
        //System.out.println("Your JRE version: " + version);
        char minor = version.charAt(2);
        int pos = version.indexOf("_");
        int update = -1;
       
        if (pos != -1) {
    	   try {
    		   update = Integer.valueOf(version.substring(pos+1));
    	   } catch (NumberFormatException e) { 
    		   pos = version.indexOf("-");
    		   if (pos != -1) {
    			   try {
    	    		   update = Integer.valueOf(version.substring(pos+1));
    	    	   } catch (NumberFormatException e2) {}	   
    		   }
    		   
    	   }
        }
       
        if(minor < '6') {
    	   System.out.println("\nYou need at least JRE 1.6.0_17 to run this program!");
    	   System.out.println("\nValidation not successful!\n\n");
    	   System.exit(1);
        }
        else {
    	   if (minor == '6' && update < 17) {
    		   System.out.println("\nYou need at least JRE 1.6.0_17 to run this program!");
    		   System.out.println("\nValidation not successful!\n\n");
    		   System.exit(1);
        	   return;
    	   }
        }
		
        if (args.length < 2 || args.length > 3) {
   		   System.out.println("Needing a file to validate and intermediate file");
   		   printUsage();
   		   System.out.println("\nValidation not successful!\n\n");
   		   System.exit(2);
		}
        
        // we need the path to the directory we work in
        // because of usage on web servers with different working directorise
        try {
        	String path = Validator.class.getProtectionDomain().getCodeSource().getLocation().getPath();
			path = URLDecoder.decode(path.substring(0, path.lastIndexOf("/") + 1), "UTF-8") + "../";	//Added the ../ part since the decoded path is in the bin folder, and we run ant above this
			Definitions.DASHXSDNAME = path + Definitions.DASHXSDNAME;
			Definitions.XSLTFILE = path + Definitions.XSLTFILE;
		} catch (UnsupportedEncodingException e1) {
			System.out.println("Unable to get current path to executable!");
			System.out.println("\nValidation not successful!\n\n");
			System.exit(3);
		}
        
	    Definitions.tmpOutputFile_ = args[1];	//Previously, this software was saving all temp files into java.io.tmpdir, so two parallel sessions could overwrite the same

		try {
			String OS = System.getProperty("os.name").toLowerCase();
			if (OS.indexOf("windows") == -1)
				system_os = "unix";

			
			boolean retVal = false;
			
			String pathToMPD = args[0];
			URL pathToXSD = null;
			if (args.length == 3 && args[2] != null && !args[2].equals("")) {
				if (args[2].contains(":"))
					pathToXSD = new URL(args[2]);
				else {
					File f = new File(args[2]);
					
					if (system_os.equals("windows"))
						pathToXSD = new URL("file:///" + f.getAbsolutePath());
					else
						pathToXSD = new URL("file://" + f.getAbsolutePath());
				}
			}

			// Step 1:
			// XLink resolving and validation
			System.out.println("\nStart XLink resolving\n=====================\n");
			
			XLinkResolver xlinkResolver = new XLinkResolver();
			xlinkResolver.resolveXLinks(pathToMPD);		
			
			System.out.println("XLink resolving successful\n\n");
			
			URL url = null;
			if (system_os.equals("windows")) {
				url = new URL("file:///" + Definitions.tmpOutputFile_);
			}
			else { // in linux there are different separators
				url = new URL("file://" + Definitions.tmpOutputFile_);
				String separator = System.getProperty("file.separator");
				Definitions.DASHXSDNAME = Definitions.DASHXSDNAME.replace("\\", separator);
				Definitions.XSLTFILE = Definitions.XSLTFILE.replace("\\", separator);
			}			
			
			// Step 2:
			// MPD validation
			System.out.println("\nStart MPD validation\n====================\n");
			retVal = parseDASH(url, pathToXSD);
			if (retVal)
				System.out.println("MPD validation successful - DASH is valid!\n\n");
			else {
				System.out.println("MPD validation not successful - DASH is not valid!\n\n");
				System.exit(4);
			}
			
			// Step 3:
			// Schematron check
			System.out.println("\nStart Schematron validation\n===========================\n");
			retVal = XSLTTransformer.transform(Definitions.tmpOutputFile_, Definitions.XSLTFILE);
			if (retVal)
				System.out.println("Schematron validation successful - DASH is valid!\n\n");
			else
				System.out.println("Schematron validation not successful - DASH is not valid!\n\n");
		} catch (MalformedURLException e) {
			System.out.println("Malformed URL: " + e.getMessage());
			System.out.println("\nValidation not successful!\n\n");
			System.exit(5);
		} catch (SAXException e) {
			System.out.println("SAXException: " + e.getMessage());
			System.out.println("\nValidation not successful!\n\n");
			System.exit(6);
		} catch (IOException e) {
			System.out.println("IOException: " + e.getMessage());
			System.out.println("\nValidation not successful!\n\n");
			System.exit(7);
		} catch (ParserConfigurationException e) {
			System.out.println("ParserConfigurationException: " + e.getMessage());
			System.out.println("\nValidation not successful!\n\n");
			System.exit(8);
		} catch (XPathExpressionException e) {
			System.out.println("XPathExpressionException: " + e.getMessage());
			System.out.println("\nValidation not successful!\n\n");
			System.exit(9);
		} catch (XLinkException e) {
			System.out.println("XLinkException: " + e.getMessage());
			System.out.println("\nValidation not successful!\n\n");
			System.exit(10);
		} catch (TransformerException e) {
			System.out.println("TransformerException: " + e.getMessage());
			System.out.println("\nValidation not successful!\n\n");
			System.exit(11);
		} catch (Exception e) {
			System.out.println("Unexpected error: " + e.getMessage());
			e.printStackTrace();
			System.out.println("\nValidation not successful!\n\n");
			System.exit(12);
		} finally {
			// delete the temporary file
			File tmpFile = new File(Definitions.tmpOutputFile_);
			if (tmpFile != null && tmpFile.exists())
				tmpFile.delete();
		}
	}

	private static boolean parseDASH(URL pathToFile, URL pathToXSD) throws URISyntaxException {
		JAXBContext jaxbContext;
		try {
			jaxbContext = JAXBContext.newInstance(MPD.class);
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			SchemaFactory sf = SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
						
			Schema schema = null;
			
			if (pathToXSD != null)
				schema = sf.newSchema(pathToXSD);
			else {
				// use default schema
				File f = new File(Definitions.DASHXSDNAME);
				if (!f.exists()) {
					System.out.println("Schema cannot be found!");
					throw new JAXBException("Schema not found");
				}
				
				schema = sf.newSchema(f);
			}
			unmarshaller.setSchema(schema);
			EventHandler e = new EventHandler();
			unmarshaller.setEventHandler(e);
			unmarshaller.unmarshal(new StreamSource(pathToFile.openStream()), MPD.class);
			if (e.hasErrors())
				return false;
				
			return true;
		} catch (JAXBException e) {
			System.out.println("Parsing error: " + e.getMessage());
			return false; // we have errors
		} catch (Exception e) {
			System.out.println("Unexpected error: " + e.getMessage());
			return false; // we have errors
		}
	}
	
	public static void printUsage() {
		System.out.println("usage: Validator <file> <Resolved file> [<XSD>]");
		System.out.println("=======================");
		System.out.println("<file>   file to be validated");
		System.out.println("<Resolved file>    Name (optionally including path) to store the intermediate xlink resolved file");
		System.out.println("<XSD>    XSD to be used for validation (optional)");
		System.out.println("");
	}

}
