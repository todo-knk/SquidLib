package squidpony.squidgrid.gui;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import squidpony.SColor;
import squidpony.squidgrid.DirectionIntercardinal;

/**
 * Class for creating text blocks.
 *
 * Please refer to the {@link squidpony.squidgrid.gui.TextCellFactoryBuilder} class documentation regarding the 
 * defaults and sizing options for TextCellFactory objects.
 *
 * In order to easily support Unicode, strings are treated as a series of code points.
 *
 * All images have transparent backgrounds.
 *
 * @author Eben Howard - http://squidpony.com - howard@squidpony.com
 */
public class TextCellFactory {

    /**
     * The commonly used symbols in roguelike games.
     */
    public static final String DEFAULT_FITTING = "@!#$%^&*()_+1234567890-=~ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz;:,'\"{}?/\\";

    private int verticalOffset = 0, horizontalOffset = 0;//how far the baseline needs to be moved based on squeezing the cell size
    private Font font;
    private String fitting;
    private Set<Integer> largeCharacters;//size on only the largest characters, determined as sizing is performed
    private boolean antialias = false;
    private int leftPadding = 0, rightPadding = 0, topPadding = 0, bottomPadding = 0;
    private int width = 1, height = 1;
    private ImageCellMap map;

    public TextCellFactory(TextCellFactoryBuilder builder) {
        font = builder.font;
        fitting = builder.fitting;
        antialias = builder.antialias;
        leftPadding = builder.leftPadding;
        rightPadding = builder.rightPadding;
        topPadding = builder.topPadding;
        bottomPadding = builder.bottomPadding;
        width = builder.width;
        height = builder.height;
        if (width <= 0 || height <= 0) {
            map = new ImageCellMap();
            initializeByFont(font);
            map = new ImageCellMap(width, height);
        } else {
            map = new ImageCellMap(width, height);
            initializeBySize(width, height, font);
        }
    }

    /**
     * Returns the font used by this factory.
     *
     * @return
     */
    public Font font() {
        return font;
    }

    /**
     * Returns the width of a single cell.
     *
     * @return
     */
    public int width() {
        return width;
    }

    /**
     * Returns the height of a single cell.
     *
     * @return
     */
    public int height() {
        return height;
    }

    private void initializeByFont(Font font) {
        this.font = font;
        largeCharacters = new HashSet<>();
        map.clear();
        sizeCellByFont();
        map.clear();
    }

    private void initializeBySize(int cellWidth, int cellHeight, Font font) {
        this.width = cellWidth;
        this.height = cellHeight;
        this.font = font;
        largeCharacters = new HashSet<>();
        map.clear();
        sizeCellByDimension();
        map.clear();
    }

    private void sizeCellByFont() {
        width = 1;
        height = 1;

        //temporarily remove padding values
        int tempLeftPadding = leftPadding;
        int tempRightPadding = rightPadding;
        int tempTopPadding = topPadding;
        int tempBottomPadding = bottomPadding;
        leftPadding = 0;
        rightPadding = 0;
        topPadding = 0;
        bottomPadding = 0;
        verticalOffset = 0;
        horizontalOffset = width / 2;

        findSize();

        //restore cell sizes based on padding
        leftPadding = tempLeftPadding;
        rightPadding = tempRightPadding;
        topPadding = tempTopPadding;
        bottomPadding = tempBottomPadding;
        verticalOffset += topPadding;
        horizontalOffset += leftPadding;
        width += leftPadding + rightPadding;
        height += topPadding + bottomPadding;
    }

    private void findSize() {
        int left = Integer.MAX_VALUE, right = Integer.MIN_VALUE,
                top = Integer.MAX_VALUE, bottom = Integer.MIN_VALUE;
        HashMap<DirectionIntercardinal, Integer> larges = new HashMap<>();

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g = image.createGraphics();
        g.setFont(font);

        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        if (antialias) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        } else {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        }

        FontRenderContext context = g.getFontRenderContext();

        for (int i = 0; i < fitting.length();) {
            int code = fitting.codePointAt(i);
            GlyphVector vect = font.createGlyphVector(context, Character.toChars(code));
            if (vect.getGlyphCode(0) != font.getMissingGlyphCode()
                    && Character.isValidCodePoint(code)
                    && !Character.isISOControl(code)
                    && !Character.isWhitespace(code)) {
                Rectangle rect = vect.getGlyphPixelBounds(0, context, 0, 0);
                if (rect.x < left) {
                    larges.put(DirectionIntercardinal.LEFT, code);
                    left = rect.x;
                }
                if (rect.y < top) {
                    larges.put(DirectionIntercardinal.UP, code);
                    top = rect.y;
                }
                if (rect.x + rect.width > right) {
                    larges.put(DirectionIntercardinal.RIGHT, code);
                    right = rect.x + rect.width;
                }
                if (rect.y + rect.height > bottom) {
                    larges.put(DirectionIntercardinal.DOWN, code);
                    bottom = rect.y + rect.height;
                }
            }
            i += Character.charCount(code);
        }
        largeCharacters = new HashSet<>(larges.values());

        width = right - left;
        width *= 2;
        height = bottom - top;
        height *= 2;
        horizontalOffset = width / 2;
        verticalOffset = 0;
        if (width > 0 && height > 0) {
            trimCell();
        }
    }

    private void trimCell() {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        //find best horizontal offset
        int bestHorizontalOffset = Integer.MIN_VALUE;
        for (int code : largeCharacters) {
            int tempHorizontalOffset = horizontalOffset;
            if (visible(code, image)) {//only calculate on printable characters
                while (horizontalOffset > -width && willFit(code)) {
                    horizontalOffset--;
                }
            }
            bestHorizontalOffset = Math.max(bestHorizontalOffset, Math.min(tempHorizontalOffset, horizontalOffset + 1));//slide back one to the right if we slid left
            horizontalOffset = tempHorizontalOffset;
        }
        horizontalOffset = bestHorizontalOffset;

        //find best vertical offset
        int bestVerticalOffset = Integer.MIN_VALUE;
        for (int code : largeCharacters) {
            int tempVerticalOffset = verticalOffset;
            while (verticalOffset > -height && willFit(code)) {
                verticalOffset--;
            }
            bestVerticalOffset = Math.max(bestVerticalOffset, Math.min(tempVerticalOffset, verticalOffset + 1));//slide back down one if slid up
            verticalOffset = tempVerticalOffset;
        }
        verticalOffset = bestVerticalOffset;

        int bestWidth = 1;
        int bestHeight = 1;

        //squeeze back down to rectangle cell
        for (int code : largeCharacters) {

            //squeeze width
            int tempWidth = width;
            while (width > 0 && willFit(code)) {
                width--;
            }
            bestWidth = Math.max(bestWidth, width + 1);//take whatever the largest needed width so far is
            width = tempWidth;

            //squeeze height
            int tempHeight = height;
            while (height > 0 && willFit(code)) {
                height--;
            }
            bestHeight = Math.max(bestHeight, height + 1);//take whatever the largest needed height so far is
            height = tempHeight;
        }

        //set cell sizes based on found best sizes
        width = bestWidth;
        height = bestHeight;
    }

    private void sizeCellByDimension() {
        int desiredWidth = width;
        int desiredHeight = height;

        //try provided font size first
        initializeByFont(font);

        if (width > desiredWidth || height > desiredHeight) {//try font sizes and take largest that fits if passed in font is too large
            int largeSide = Math.max(width, height);
            if (largeSide > 0) {
                int fontSize = font.getSize();
                int largeDesiredSide = Math.max(desiredWidth, desiredHeight);
                fontSize = largeDesiredSide * fontSize / largeSide;//approximately the right ratio
                fontSize *= 1.2;//pad just a bit

                do {
                    font = new Font(font.getFontName(), font.getStyle(), fontSize);
                    initializeByFont(font);
                    fontSize--;
                } while (width > desiredWidth || height > desiredHeight);

                horizontalOffset += (desiredWidth - width) / 2;//increase by added width, err on the side of one pixel left of center
                verticalOffset += (desiredHeight - height) / 2;
                width = desiredWidth;
                height = desiredHeight;
            }
        }

        map.clear();
    }

    private boolean visible(int code, BufferedImage i) {
        BufferedImage testImage = makeMonoImage(code, i);
        //work from middle out to maximize chance of finding visilble bit
        int startx = testImage.getWidth() / 2;
        for (int x = 0; x <= startx; x++) {
            for (int y = 0; y < testImage.getHeight(); y++) {
                if (startx + x < testImage.getWidth()) {//make sure no overflow on rounding
                    if (testImage.getRGB(x + startx, y) != Color.WHITE.getRGB() || testImage.getRGB(startx - x, y) != Color.WHITE.getRGB()) {
                        return true;//found a filled in pixel
                    }
                }
            }
        }

        return false;//no pixels found
    }

    private boolean willFit(int code, BufferedImage image) {
        if (Character.isISOControl(code)) {//make sure it's a printable character
            return true;
        }

        for (DirectionIntercardinal dir : DirectionIntercardinal.CARDINALS) {
            if (!testSlide(code, dir, image)) {
                return false;
            }
        }

        //all the needed space was clear!
        return true;
    }

    /**
     * Returns true if the given character will fit inside the current cell dimensions with the current font.
     *
     * ISO Control characters, non-printing characters and invalid unicode characters are all considered by definition
     * to fit.
     *
     * @param codepoint
     * @return
     */
    public boolean willFit(int codepoint) {
        if (!Character.isValidCodePoint(codepoint) || Character.isISOControl(codepoint)) {
            return true;
        }

        return willFit(codepoint, new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY));
    }

    /**
     * Slides the character one pixel in the provided direction and test if fully exposed the opposite edge. Returns true
     * if opposite edge was fully exposed.
     *
     * @param code
     * @param dir
     * @return
     */
    private boolean testSlide(int code, DirectionIntercardinal dir, BufferedImage image) {
        //set offsets in a direction to test if it cleared the space
        horizontalOffset += dir.deltaX;
        verticalOffset += dir.deltaY;

        BufferedImage testImage = makeMonoImage(code, image);

        //reset offsets to actual values
        horizontalOffset -= dir.deltaX;
        verticalOffset -= dir.deltaY;

        int startx = 0, starty = 0, endx = 0, endy = 0;//end points should be included in check
        switch (dir) {//set values to check edge opposite of movement
            case RIGHT:
                endy = height - 1;
                break;
            case LEFT:
                startx = width - 1;
                endx = startx;
                endy = height - 1;
                break;
            case UP:
                endx = width - 1;
                starty = height - 1;
                endy = starty;
                break;
            case DOWN:
                endx = width - 1;
        }

        //test for edge hit
        for (int x = startx; x <= endx; x++) {
            for (int y = starty; y <= endy; y++) {
                if (testImage.getRGB(x, y) != Color.WHITE.getRGB()) {
                    return false;//found an edge that would normally be printed off the cell
                }
            }
        }
        return true;
    }

    /**
     * Returns the image of the character represented by the passed in code point.
     *
     * @param code the unicode code point for the character to draw
     * @param color the color to draw
     * @return
     */
    public BufferedImage get(int code, Color color) {
        String search = asKey(code, color);
        BufferedImage block = map.get(search);

        if (block == null) {
            block = makeImage(code, color);
            map.put(search, block);
        }
        return block;
    }

    /**
     * Returns the string used as a key for hash mapping. Useful for drop-in replacement of text and images.
     *
     * @param code the unicode code point for the character to draw
     * @param color the color to draw
     * @return
     */
    public String asKey(int code, Color color) {
        return new String(Character.toChars(code)) + " " + color.getClass().getSimpleName() + ": " + Integer.toHexString(color.getRGB());
    }

    /**
     * Returns a solid block of the provided color.
     *
     * @param color
     * @return
     */
    public BufferedImage getSolid(Color color) {
        String search = "Solid " + color.getClass().getSimpleName() + ": " + Integer.toHexString(color.getRGB());
        BufferedImage block = map.get(search);

        if (block == null) {
            block = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
            Graphics2D g = block.createGraphics();
            g.setColor(color);
            g.fillRect(0, 0, block.getWidth(), block.getHeight());
            map.put(search, block);
        }
        return block;
    }

    private BufferedImage makeMonoImage(int code, BufferedImage i) {
        Graphics2D g = i.createGraphics();
        g.setColor(SColor.WHITE);
        g.fillRect(0, 0, i.getWidth(), i.getHeight());
        drawForeground(g, code, Color.BLACK);
        return i;
    }

    private BufferedImage makeImage(int code, Color color) {
        BufferedImage i = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g = i.createGraphics();
        drawForeground(g, code, color);
        return i;
    }

    private void drawForeground(Graphics2D g, int code, Color color) {
        g.setColor(color);
        g.setFont(font);

        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        if (antialias) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        } else {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        }

        int x = horizontalOffset - g.getFontMetrics().charWidth(code) / 2;//start with half of the character's width
        int y = g.getFontMetrics().getMaxAscent() + verticalOffset;
        g.drawString(new String(Character.toChars(code)), x, y);
    }

}
