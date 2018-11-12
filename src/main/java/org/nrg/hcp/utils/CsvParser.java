package org.nrg.hcp.utils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import au.com.bytecode.opencsv.CSVReader;

public class CsvParser {
	
	public static List<Map<String,String>> parseCSV(String fileName) throws IOException{
		return parseCSV(new CSVReader(new FileReader(fileName)));
	}
	
	public static List<Map<String,String>> parseCSV(File inFile) throws IOException{
		return parseCSV(new CSVReader(new FileReader(inFile)));
	}
	
	public static List<Map<String,String>> parseCSVContents(String fileContents) throws IOException{
		return parseCSV(new CSVReader(new StringReader(fileContents)));
	}
	
	/**
	 * Converts the CSV file, in which the columns represent the metrics and the rows 
	 * represent the scans, into a list of maps.  Each map in this list represents 
	 * one row from the file.  The map is keyed by the column header and holds the 
	 * value for that row at that column.
	 * @throws IOException 
	 */
	 static List<Map<String,String>> parseCSV(CSVReader csvReader) throws IOException{
		ArrayList<Map<String,String>> rows = new ArrayList<Map<String,String>>(); 
		List<String[]> lines = csvReader.readAll();
        String[] header;
        int startIndex;

		if (lines.size() >= 2) {
            if (lines.get(0)[0].contains("Study Name:")) { // Indicates Toolbox export
                header = lines.get(2);
                startIndex = 3;
            } else {
			    header = lines.get(0);
                startIndex = 1;
            }
			for (int i=startIndex; i<lines.size(); i++) {
				String[] line = lines.get(i);
				LinkedHashMap<String, String> map=new LinkedHashMap<String, String>();
				for (int j=0;j<line.length;j++) {
					map.put(header[j], line[j]);
				}
				rows.add(map);
			}
		}
		return rows;

	}
}
