package squidpony.squidgrid.gui.gdx;

import com.badlogic.gdx.graphics.Color;

import squidpony.IFilter;
import squidpony.IColorCenter;

/**
 * A concrete implementation of {@link IColorCenter} for libgdx's {@link com.badlogic.gdx.graphics.Color}.
 *
 * @author smelC
 * @author Tommy Ettinger
 * @see SColor Another way to obtain colors by using pre-allocated (and named) instances.
 */
public class SquidColorCenter extends IColorCenter.Skeleton<Color> {

    /**
     * A fresh filter-less color center.
     */
    public SquidColorCenter()
    {
    	this(null);
    }

    /**
	 * A fresh filter-less color center.
	 * 
	 * @param filterEffect
	 *            The filter to use.
	 */
    public SquidColorCenter(/*Nullable*/IFilter<Color> filterEffect)
    {
    	super(filterEffect);
    }
	@Override
	protected Color create(int red, int green, int blue, int opacity) {
		if (filter == null)
			/* No filtering */
			return new Color(red / 255f, green / 255f, blue / 255f, opacity / 255f);
		else
			/* Some filtering */
			return filter.alter(red / 255f, green / 255f, blue / 255f, opacity / 255f);
	}
    @Override
	public Color filter(Color c)
    {
        if(c == null)
            return Color.CLEAR;
        else
        	return super.filter(c);
    }
    public Color get(long c)
    {
        return get((int)((c >> 24) & 0xff), (int)((c >> 16) & 0xff), (int)((c >> 8) & 0xff), (int)(c & 0xff));
    }
    public Color get(float r, float g, float b, float a)
    {
        return get(Math.round(255 * r), Math.round(255 * g), Math.round(255 * b), Math.round(255 * a));
    }

    /**
     * Gets the linear interpolation from Color start to Color end, changing by the fraction given by change.
     * @param start the initial Color
     * @param end the "target" color
     * @param change the degree to change closer to end; a change of 0.0f produces start, 1.0f produces end
     * @return a new Color
     */
    public Color lerp(Color start, Color end, float change)
    {
        return get(
                start.r + change * (end.r - start.r),
                start.g + change * (end.g - start.g),
                start.b + change * (end.b - start.b),
                start.a + change * (end.a - start.a)
        );

    }
	@Override
	public int getRed(Color c) {
		return Math.round(c.r * 255f);
	}

	@Override
	public int getGreen(Color c) {
		return Math.round(c.g * 255f);
	}

	@Override
	public int getBlue(Color c) {
		return Math.round(c.b * 255f);
	}

	@Override
	public int getAlpha(Color c) {
		return Math.round(c.a * 255f);
	}

    public static long encode (Color color) {
        if (color == null)
            return 0L;
        return (Math.round(color.r * 255.0) << 40) | (Math.round(color.g * 255.0) << 24) | (Math.round(color.b * 255.0) << 8) | Math.round(color.a * 255.0);
    }

    @Override
    public String toString() {
        return "SquidColorCenter{" +
                "filter=" + (filter == null ? "null" : filter.getClass().getSimpleName()) +
                '}';
    }
}