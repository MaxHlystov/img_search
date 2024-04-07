package ru.fmtk.khlystov.image_detect;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.lang.Math.abs;

public class Main {

    // sx,sy - bounds of the hash of the first part of picture
    // ex, ey - bounds of the hash of the second part of picture
    private static final Map<Rectangle, String> hashOfNumbers = new HashMap<>();
    private static final Map<String, HashSet<Integer>> fBoundsByNumber = new HashMap<>();
    private static final Map<String, HashSet<Integer>> lBoundsByNumber = new HashMap<>();
    private static final TreeMap<Integer, String> suitsByHash = new TreeMap<>();
    public static final int SUIT_START_Y = 27;

    static {
        hashOfNumbers.put(new Rectangle(253, 437), "2");

        suitsByHash.put(98, "c");
        suitsByHash.put(107, "c");

        suitsByHash.put(115, "s");
        suitsByHash.put(152, "s");

        suitsByHash.put(-93, "d");

        suitsByHash.put(-114, "h");
        suitsByHash.put(-101, "h");

    }

    public static final Rectangle SEARCH_AREA = new Rectangle(100, 380, 520 - 100, 830 - 380);
    private static final int TONE_SENSITIVITY = 10;
    public static final int LIGHT_GRAY_TONE = 0x75;
    public static final int BLACK_TONE = 0x23;
    private static final int RED_TONE = 0xCD;
    public static final int CARD_SIZE_RUN = 12;
    private static final int CARD_WIDTH = 52;
    public static final int CARD_HEIGHT = 74;

    public static void main(String[] args) {
        Consumer<String> logger = System.out::println;

        if (args.length == 0 || args[0].trim().isEmpty()) {
            logger.accept("You need to specify directory for searching.");
            return;
        }
        logger.accept("Parse png files in directory " + args[0]);
        try {
            streamFileNamesInDir(args[0]).stream().map(path -> path + " - " + processFile(logger, path)).forEach(logger);
        } catch (NoSuchFileException e) {
            logger.accept("Directory " + args[0] + " does not exist.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        logger.accept("=========================== hash 1,2 for numbers");
        Set<String> numbers = new HashSet<>(fBoundsByNumber.keySet());
        numbers.addAll(lBoundsByNumber.keySet());
        numbers.forEach(number -> {
            logger.accept("=== " + number);
            var points = fBoundsByNumber.get(number);
            if (points != null) {
                logger.accept(" fBoundsByNumber.put(\"" + number + "\", List.of" + points.stream()
                        .sorted()
                        .map(i -> i.toString())
                        .toList()
                + ");");
            }

            points = lBoundsByNumber.get(number);
            if (points != null) {
                logger.accept(" lBoundsByNumber.put(\"" + number + "\", List.of" + points.stream()
                        .sorted()
                        .map(i -> i.toString())
                        .toList()
                        + ");");
            }

//
//
//            final int[] x = new int[]{0, Integer.MIN_VALUE, Integer.MAX_VALUE, 0}; // -min,-max, +min, +max
//            points.forEach(p -> {
//                int idx = p.x < 0 ? 0 : 2;
//                x[0] = min(x[idx], p.x);
//                x[1] = max(x[idx + 1], p.x);
//                idx = p.y < 0 ? 0 : 2;
//                y[0] = min(y[idx], p.y);
//                y[1] = max(y[idx + 1], p.y);
//
//            });
//
//            logger.accept(" " + Arrays.toString(x));
        });
    }

    private static List<Path> streamFileNamesInDir(String dirName) throws IOException {
        try (Stream<Path> stream = Files.list(Paths.get(dirName))) {
            return stream.filter(file -> !Files.isDirectory(file) && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")).toList();
        }
    }

    private static String processFile(Consumer<String> logger, Path pathToFile) {
        final String writeName = pathToFile.getFileName().toString();
        final String cards = writeName.substring(0, writeName.lastIndexOf('.'));
        final String cardsNorm = cards.replace("10", "1");
        final StringBuilder result = new StringBuilder();
        try {
            final BufferedImage image = ImageIO.read(pathToFile.toFile());
            BufferedImage searchArea = image.getSubimage(SEARCH_AREA.x, SEARCH_AREA.y, SEARCH_AREA.width, SEARCH_AREA.height);
            write(pathToFile, "t/" + writeName, searchArea);

            List<Rectangle> cardsToCheck = findPotentialCards(searchArea);
            final int[] i = new int[]{0};
            cardsToCheck.forEach(card -> {
                BufferedImage numberImg = searchArea.getSubimage(card.x, card.y, 30, 23); //toBwImage();
                Point suitStart = fitBoundRightDown(searchArea,
                        card.x, card.y + SUIT_START_Y,
                        card.x + 12, card.y + SUIT_START_Y + 21,
                        10, 10);
                if (suitStart == null) {
                    suitStart = new Point(card.x, card.y);
                }
                BufferedImage suitImg = searchArea.getSubimage(suitStart.x, suitStart.y, 16, 21);
                boolean isRed = isRed(suitImg);
                int numberHash1 = (isRed ? -1 : 1) *
                        countPixels(numberImg.getSubimage(0, 0, numberImg.getWidth(), numberImg.getHeight()));
                int numberHash2 = countPixels(numberImg.getSubimage(11, 0, numberImg.getWidth() - 11, numberImg.getHeight()));
                int suitHash = countPixels(suitImg);

                String number = getMostFitted(hashOfNumbers, numberHash1, numberHash2);
                String suit = getMostFitted(suitsByHash, (isRed ? -1 : 1) * suitHash);
                if (number != null && suit != null) {
                    result.append(number);
                    result.append(suit);
                }
                String realNumber = cardsNorm.substring(i[0] * 2, i[0] * 2 + 1);
                String realSuit = cardsNorm.substring(i[0] * 2 + 1, i[0] * 2 + 2);
                logger.accept("Card: (" + card.x + ", " + card.y + ", " + card.width + ", " + card.height + ") = "
                        + numberHash1 + "." + numberHash2 + "_" + (isRed ? 'R' : "B") + suitHash + "="
                        + realNumber + realSuit + " >>> " + number + suit);

                fBoundsByNumber.computeIfAbsent(realNumber, k -> new HashSet<>())
                        .add(numberHash1);
                lBoundsByNumber.computeIfAbsent(realNumber, k -> new HashSet<>())
                        .add(numberHash2);

                write(pathToFile, "t/" + "_N_" + realNumber + "_" + writeName, numberImg);
                write(pathToFile, "t/" + "_S_" + realSuit + "_" + writeName, suitImg);
                write(pathToFile, "t/" + "x" + card.x + 'y' + card.y + '_' + writeName, searchArea.getSubimage(card.x, card.y, CARD_WIDTH, CARD_HEIGHT));
                i[0] = i[0] + 1;
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final String cardsComputed = result.toString();
        if (!cards.equals(cardsComputed)) {
            logger.accept("-------- " + cards + " || " + cardsComputed);
        }
        return result.isEmpty() ? "Found nothing" : cardsComputed;
    }

    private static String getMostFitted(Map<Rectangle, String> hashOfNumbers, int hash1, int hash2) {
        return hashOfNumbers.entrySet().stream()
                .filter(entry -> {
                    var r = entry.getKey();
                    return hash1 > r.x && hash1 < r.y && hash2 > r.width && hash2 < r.height;
                })
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static BufferedImage toBwImage(BufferedImage image) {
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_BINARY);

        Graphics2D graphic = result.createGraphics();
        graphic.drawImage(image, 0, 0, Color.WHITE, null);
        graphic.dispose();
        return result;
    }

    private static String getMostFitted(TreeMap<Integer, String> values, int hash) {
        Integer ceil = values.ceilingKey(hash);
        if (ceil != null && ceil == hash) {
            return values.get(ceil);
        }
        Integer floor = values.floorKey(hash);
        if (ceil == null && floor == null) {
            return null;
        }
        if (floor == null) {
            return values.get(ceil);
        }
        if (ceil == null || abs(floor - hash) < abs(ceil - hash)) {
            return values.get(floor);
        }
        return values.get(ceil);
    }

    private static void write(Path pathToFile, String name, BufferedImage searchArea) {
        Path write = pathToFile.getParent().resolve(name);
        try {
            ImageIO.write(searchArea, "png", write.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static int countPixels(BufferedImage img) {
        int count = 0;
        for (int y = 0; y < img.getHeight(); ++y) {
            for (int x = 0; x < img.getWidth(); ++x) {
                final int rgb = img.getRGB(x, y);
                if (!isWhiteOrGrey(rgb)) {
                    ++count;
                }
            }
        }
        return count;
    }

    private static boolean isRed(BufferedImage img) {
        int redCount = 0;
        int notRed = 0;
        for (int y = 0; y < img.getHeight(); ++y) {
            for (int x = 0; x < img.getWidth(); ++x) {
                final int rgb = img.getRGB(x, y);
                if (isRed(rgb)) {
                    ++redCount;
                } else if (isBlack(rgb)) {
                    ++notRed;
                }
            }
        }
        return redCount > notRed;
    }

    private static String toRGBString(int rgb) {
        int r1 = ((rgb >> 16) & 0xFF);
        int g1 = ((rgb >> 8) & 0xFF);
        int b1 = (rgb & 0xFF);
        return String.format("#%02X%02X%02X", r1, g1, b1);
    }

    private static List<Rectangle> findPotentialCards(BufferedImage image) {
        final Set<Rectangle> rectangles = new HashSet<>();
        int w = image.getWidth() - CARD_WIDTH - 2;
        int h = image.getHeight() - CARD_HEIGHT - 2;
        int y = 0;
        while (y < h) {
            int x = 0;
            boolean isFound = false;
            while (x < w) {
                if (isCardStripeHorizontal(image, x, y, CARD_WIDTH) && isCardStripeHorizontal(image, x, y + SUIT_START_Y + 1, CARD_WIDTH)) {
                    Point start = fitBoundRightDown(image, x, y, x + CARD_SIZE_RUN, y + CARD_SIZE_RUN, CARD_WIDTH + 1, CARD_HEIGHT + 2);
                    if (start != null) {
                        x = start.x;
                        rectangles.add(new Rectangle(start.x + 1, start.y + 1, CARD_WIDTH, CARD_HEIGHT));
                    }
                    x += CARD_WIDTH + 1;
                } else {
                    ++x;
                }
            }
            ++y;
        }
        return rectangles.stream().sorted(Comparator.comparing(Rectangle::getY).thenComparing(Rectangle::getX)).toList();
    }

    // Search last white stripe in horizontal and vertical
    private static Point fitBoundRightDown(BufferedImage img, int x, int y, int w, int h, int stripeW, int stripeH) {
        if (!isCardStripeVertical(img, x, y, stripeH)) {
            return null;
        }
        for (++y; y < h; ++y) {
            if (!isCardStripeHorizontal(img, x, y, stripeW)) {
                --y;
                break;
            }
        }
        for (++x; x < w; ++x) {
            if (!isCardStripeVertical(img, x, y, stripeH)) {
                --x;
                break;
            }
        }
        return new Point(x, y);
    }

    private static boolean isCardStripeHorizontal(BufferedImage img, int x, int y, int w) {
        // We can potentially optimize it, by vectorization, but I've done it in C, not Java.
        int maxX = x + w;
        for (; x < maxX; ++x) {
            final int rgb = img.getRGB(x, y);
            if (!isWhiteOrGrey(rgb)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCardStripeVertical(BufferedImage img, int x, int y, int stripeH) {
        int h = y + stripeH;
        for (; y < h; ++y) {
            final int rgb = img.getRGB(x, y);
            String v = toRGBString(rgb);
            if (!isWhiteOrGrey(rgb)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isRed(int rgb) {
        int r1 = ((rgb >> 16) & 0xFF);
        int g1 = ((rgb >> 8) & 0xFF);
        int b1 = (rgb & 0xFF);
        return r1 >= RED_TONE && (r1 - g1) > TONE_SENSITIVITY && (r1 - b1) > TONE_SENSITIVITY && abs(g1 - b1) <= TONE_SENSITIVITY;
    }

    private static boolean isWhiteOrGrey(int rgb) {
        int r1 = ((rgb >> 16) & 0xFF);
        int g1 = ((rgb >> 8) & 0xFF);
        int b1 = (rgb & 0xFF);
        return r1 >= LIGHT_GRAY_TONE && g1 >= LIGHT_GRAY_TONE && b1 >= LIGHT_GRAY_TONE;
    }

    private static boolean isBlack(int rgb) {
        int r1 = ((rgb >> 16) & 0xFF);
        int g1 = ((rgb >> 8) & 0xFF);
        int b1 = (rgb & 0xFF);
        return r1 <= BLACK_TONE && g1 <= BLACK_TONE && b1 <= BLACK_TONE;
    }

    public static int[] findSubimage(BufferedImage im1, BufferedImage im2) {
        int w1 = im1.getWidth();
        int h1 = im1.getHeight();
        int w2 = im2.getWidth();
        int h2 = im2.getHeight();
        assert (w2 <= w1 && h2 <= h1);
        // will keep track of best position found
        int bestX = 0;
        int bestY = 0;
        double lowestDiff = Double.POSITIVE_INFINITY;
        // brute-force search through whole image (slow...)
        for (int x = 0; x < w1 - w2; x++) {
            for (int y = 0; y < h1 - h2; y++) {
                double comp = compareImages(im1.getSubimage(x, y, w2, h2), im2);
                if (comp < lowestDiff) {
                    bestX = x;
                    bestY = y;
                    lowestDiff = comp;
                }
            }
        }
        // output similarity measure from 0 to 1, with 0 being identical
        System.out.println(lowestDiff);
        // return best location
        return new int[]{bestX, bestY};
    }

    /**
     * Determines how different two identically sized regions are.
     */
    public static double compareImages(BufferedImage im1, BufferedImage im2) {
        assert (im1.getHeight() == im2.getHeight() && im1.getWidth() == im2.getWidth());
        double variation = 0.0;
        for (int x = 0; x < im1.getWidth(); x++) {
            for (int y = 0; y < im1.getHeight(); y++) {
                variation += compareARGB(im1.getRGB(x, y), im2.getRGB(x, y)) / Math.sqrt(3);
            }
        }
        return variation / (im1.getWidth() * im1.getHeight());
    }

    /**
     * Calculates the difference between two ARGB colours (BufferedImage.TYPE_INT_ARGB).
     */
    public static double compareARGB(int rgb1, int rgb2) {
        double r1 = ((rgb1 >> 16) & 0xFF) / 255.0;
        double r2 = ((rgb2 >> 16) & 0xFF) / 255.0;
        double g1 = ((rgb1 >> 8) & 0xFF) / 255.0;
        double g2 = ((rgb2 >> 8) & 0xFF) / 255.0;
        double b1 = (rgb1 & 0xFF) / 255.0;
        double b2 = (rgb2 & 0xFF) / 255.0;
        //        double a1 = ((rgb1 >> 24) & 0xFF) / 255.0;
        //        double a2 = ((rgb2 >> 24) & 0xFF) / 255.0;
        //        // if there is transparency, the alpha values will make difference smaller
        //        return a1 * a2 *
        return Math.sqrt((r1 - r2) * (r1 - r2) + (g1 - g2) * (g1 - g2) + (b1 - b2) * (b1 - b2));
    }

}
