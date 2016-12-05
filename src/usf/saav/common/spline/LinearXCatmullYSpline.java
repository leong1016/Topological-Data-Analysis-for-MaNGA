package usf.saav.common.spline;

import java.util.Vector;

import usf.saav.common.MathXv1;
import usf.saav.common.types.Float2;

public abstract class LinearXCatmullYSpline extends Spline {


	//public LinearXCatmullYSpline( Float2 ... p){ super(p); }
	public LinearXCatmullYSpline( ){ super(); }
	
	
	@Override
	public Float2 interpolate(float t) {
		
		int idx0 = MathXv1.clamp( (int)(t * size() - 1), 0, size()-1 );
		int idx1 = MathXv1.clamp( (int)(t * size() + 0), 0, size()-1 );
		int idx2 = MathXv1.clamp( (int)(t * size() + 1), 0, size()-1 );
		int idx3 = MathXv1.clamp( (int)(t * size() + 2), 0, size()-1 );
		float off = (float) (t * size() - Math.floor( t * size() ));
	
		float x = MathXv1.Interpolate.Linear( getControlPoint(idx1).x, getControlPoint(idx2).x, off );
		float y = MathXv1.Interpolate.CatmullRom( getControlPoint(idx0).y, getControlPoint(idx1).y, getControlPoint(idx2).y, getControlPoint(idx3).y, off );
		
		return new Float2(x,y);
	}
	
	public static class Default extends LinearXCatmullYSpline {
		protected Vector<Float2> pnts = new Vector<Float2>( );

		public Default( Float2 ... p){ 
			for( Float2 _p : p)
				pnts.add(_p);
		}
		
		@Override public int size() { return pnts.size(); }

		@Override public Float2 getControlPoint(int i) { return pnts.get(i); }
		
	}


}
