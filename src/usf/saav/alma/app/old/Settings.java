/*
 *     ALMA TDA - Contour tree based simplification and visualization for ALMA 
 *     data cubes.
 *     Copyright (C) 2016 PAUL ROSEN
 *     
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *     
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *     
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *     
 *     You may contact the Paul Rosen at <prosen@usf.edu>. 
 */
package usf.saav.alma.app.old;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

import org.json.JSONException;
import org.json.JSONObject;

import usf.saav.common.BasicObject;
import usf.saav.common.SystemX;
import usf.saav.common.monitor.MonitoredDouble;
import usf.saav.common.monitor.MonitoredInteger;
import usf.saav.common.range.FloatRange1D;
import usf.saav.common.range.IntRange1D;


// TODO: Auto-generated Javadoc
/** Class for automatically saving settings via monitored variables.
 * 
 * @author Paul Rosen
 *
 */
public class Settings extends BasicObject {
	
	private JSONObject json;
	private String filename;
	
	/**
	 * Instantiates a new settings.
	 *
	 * @param filename the filename
	 * @throws JSONException the JSON exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public Settings( String filename ) throws JSONException, IOException {
		this.filename = filename;
		try{
			json = new JSONObject( SystemX.readFileContentsAsString(filename) );
		} catch( FileNotFoundException e ){
			json = new JSONObject();
		}
	}
	
	/** Generate a new integer variable. If the label already exists, 
	 *  the existing value is used. If not, the default value is used.
	 * @param name Label of the variable
	 * @param default_value Default value, if the variable doesn't exist already
	 * @return A new SettingsInt for the label. 
	 */
	public SettingsInt initInteger( String name, int default_value ){
		return new SettingsInt( name, default_value );
	}

	/** Generate a new double variable. If the label already exists, 
	 *  the existing value is used. If not, the default value is used.
	 * @param name Label of the variable
	 * @param default_value Default value, if the variable doesn't exist already
	 * @return A new SettingsDouble for the label.
	 */
	public SettingsDouble initDouble( String name, double default_value ){
		return new SettingsDouble( name, default_value );
	}
	
	private void serialize(){
		try {
			PrintWriter pw = new PrintWriter( filename );
			pw.print( json.toString(1) );
			//System.out.println(json.toString());
			pw.close();
		} catch (FileNotFoundException e) {
			print_error_message("Serialization failed");
		}
	}
	
	/**
	 * The Class SettingsInt.
	 */
	public class SettingsInt extends MonitoredInteger {
		IntRange1D range = null;
		String name;
		
		/**
		 * Instantiates a new settings int.
		 *
		 * @param name the name
		 * @param default_value the default value
		 */
		public SettingsInt(String name, int default_value) {
			super(0);
			this.name = name;
			try {
				set(json.getInt(name));
			} catch (JSONException j ){
				set(default_value);
			}
		}
		
		/**
		 * Sets the valid range.
		 *
		 * @param rng the new valid range
		 */
		public void setValidRange( IntRange1D rng ){
			this.range = rng;
		}

		/* (non-Javadoc)
		 * @see usf.saav.common.monitor.MonitoredInteger#set(int)
		 */
		@Override
		public void set(int newVal){
			if( range != null ){
				newVal = range.clamptoRange(newVal);
			}
			if( get() != newVal ){
				super.set(newVal);
				json.put(name, newVal);
				serialize();
			}
		}
	}
	
	/**
	 * The Class SettingsDouble.
	 */
	public class SettingsDouble extends MonitoredDouble  {
		private FloatRange1D range;
		private String name;

		/**
		 * Instantiates a new settings double.
		 *
		 * @param name the name
		 * @param default_value the default value
		 */
		public SettingsDouble(String name, double default_value) {
			this.name = name;
			try {
				set(json.getDouble(name));
			} catch (JSONException j ){
				set(default_value);
			}
		}
				
		/**
		 * Sets the valid range.
		 *
		 * @param rng the new valid range
		 */
		public void setValidRange( FloatRange1D rng ){
			this.range = rng;
		}
		
		/* (non-Javadoc)
		 * @see usf.saav.common.monitor.MonitoredDouble#set(double)
		 */
		public void set(double newVal){
			if( range != null ){
				newVal = range.clamp((float) newVal);
			}
			if( get() != newVal ){
				super.set(newVal);
				json.put(name, newVal);
				serialize();
			}
		}
	}
}