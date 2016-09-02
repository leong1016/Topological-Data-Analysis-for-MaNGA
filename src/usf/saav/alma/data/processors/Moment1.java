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
package usf.saav.alma.data.processors;

import java.util.Arrays;

import usf.saav.alma.data.ScalarField2D;
import usf.saav.alma.data.ScalarField3D;

// http://www.alma.inaf.it/images/Moments.pdf

public class Moment1 extends ScalarField2D.Default {

	int w,h;
	double [] data;
	ScalarField3D src;
	
	public Moment1( ScalarField3D src ){
		this.src = src;
		w = src.getWidth();
		h = src.getHeight();
		data = new double[w*h];
		
		Arrays.fill(data, Double.NaN);

		
		/*
		double[] denom = new double[w*h];

		Arrays.fill(data, 0);
		Arrays.fill(denom, 0);
		
		for( int z = 0; z < src.getDepth(); z++){
			//System.out.println(z + ": " + src.getCoordinate(0, 0, z)[0] +", " + src.getCoordinate(0, 0, z)[1] +", " + src.getCoordinate(0, 0, z)[2]);
			for( int y = 0; y < src.getHeight(); y++ ){
				for(int x = 0; x < src.getWidth(); x++ ){
					//double av = src.getValue(x, y, z);
					double av = Math.abs(src.getValue(x, y, z));
					//double v  = src.getCoordinate(x, y, z)[2];
					double v = z;
					data[y*w+x] += v*av;
					//data[y*w+x]  += v*av;
					denom[y*w+x] += av;
				}
			}
		}
		
		//Range r = new Range(1000,1000+src.getDepth());
		for(int i = 0; i < data.length; i++){
			//data[i] = r.clamptoRange( data[i] / denom[i] );
			data[i] = data[i] / denom[i];// - src.getDepth()/2;
		}
		*/
	}

	@Override public int getWidth() { return w; }
	@Override public int getHeight() { return h; }
	@Override public float getValue(int x, int y) {
		if( Double.isNaN( data[y*w+x] ) ){
			double denom = 0;
			data[y*w+x] = 0;
			for( int z = 0; z < src.getDepth(); z++){
				double av = Math.abs(src.getValue(x, y, z));
				data[y*w+x] += z*av;
				denom += av;
			}
			data[y*w+x] /= denom;
		}
		return (float) data[y*w+x]; 
		
	}
	
	@Override public double[] getCoordinate(int x, int y) { 
		return Arrays.copyOf(src.getCoordinate(x, y, 0),2); 
	}

	
}