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
import static java.lang.Math.min;

public class Main {

    private static final Map<String, Set<List<Integer>>> hashOfNumbers = new HashMap<>();
    private static final Map<String, Set<List<Integer>>> hashOfSuits = new HashMap<>();

    static {
        hashOfNumbers.put("A", Set.of(List.of(9, 43, 60, 68), List.of(12, 45, 56, 67), List.of(14, 45, 54, 66)));
        hashOfNumbers.put("J", Set.of(List.of(0, 24, 39, 42), List.of(0, 25, 37, 41), List.of(0, 27, 44, 41)));
        hashOfNumbers.put("K", Set.of(List.of(54, 55, 37, 48), List.of(43, 46, 37, 45), List.of(50, 54, 43, 44), List.of(51, 54, 44, 46)));
        hashOfNumbers.put("Q", Set.of(List.of(39, 41, 62, 90), List.of(41, 41, 61, 91), List.of(42, 43, 64, 91)));
        hashOfNumbers.put("10", Set.of(List.of(46, 33, 97, 97), List.of(48, 33, 94, 95), List.of(53, 40, 93, 95)));
        hashOfNumbers.put("2", Set.of(List.of(33, 51, 65, 61), List.of(22, 44, 45, 37), List.of(25, 46, 46, 36), List.of(27, 48, 53, 44)));
        hashOfNumbers.put("3", Set.of(List.of(37, 39, 73, 75), List.of(28, 26, 50, 51), List.of(30, 28, 49, 48), List.of(31, 31, 53, 52)));
        hashOfNumbers.put("4", Set.of(List.of(17, 43, 68, 76), List.of(13, 33, 56, 59), List.of(15, 35, 55, 58), List.of(16, 37, 60, 65)));
        hashOfNumbers.put("5", Set.of(List.of(62, 42, 52, 71), List.of(50, 28, 35, 49), List.of(53, 27, 32, 46), List.of(56, 30, 34, 48)));
        hashOfNumbers.put("6", Set.of(List.of(57, 69, 47, 76), List.of(48, 46, 38, 49), List.of(51, 48, 41, 51), List.of(53, 50, 41, 51)));
        hashOfNumbers.put("7", Set.of(List.of(23, 31, 55, 10), List.of(24, 34, 51, 9), List.of(27, 38, 53, 7)));
        hashOfNumbers.put("8", Set.of(List.of(44, 48, 44, 49), List.of(46, 49, 49, 53), List.of(48, 50, 46, 50)));
        hashOfNumbers.put("9", Set.of(List.of(56, 58, 58, 87), List.of(44, 37, 48, 54), List.of(47, 36, 49, 57), List.of(48, 41, 49, 57)));

        hashOfSuits.put("s", Set.of(List.of(145, 193, 93, 141), List.of(119, 178, 123, 183), List.of(98, 143, 118, 163), List.of(106, 169, 132, 193), List.of(100, 133, 132, 165), List.of(117, 139, 126, 148), List.of(121, 149, 119, 143)));
        hashOfSuits.put("c", Set.of(List.of(152, 198, 83, 140), List.of(133, 185, 121, 178), List.of(98, 143, 118, 163), List.of(104, 178, 118, 199), List.of(117, 171, 133, 191), List.of(124, 175, 128, 186), List.of(99, 148, 114, 172), List.of(103, 152, 113, 170), List.of(110, 158, 107, 163)));
        hashOfSuits.put("d", Set.of(List.of(-158, -154, -97, -89), List.of(-114, -111, -125, -117), List.of(-108, -102, -129, -124), List.of(-107, -127, -104, -125)));
        hashOfSuits.put("h", Set.of(List.of(-150, -170, -124, -104), List.of(-159, -95, -165, -101), List.of(-152, -93, -162, -110), List.of(-150, -91, -162, -111)));
    }

    public static final Rectangle SEARCH_AREA = new Rectangle(100, 380, 520 - 100, 830 - 380);
    private static final int TONE_SENSITIVITY = 10;
    public static final int LIGHT_GRAY_TONE = 0x75;
    public static final int BLACK_TONE = 0x23;
    private static final int RED_TONE = 0x60;
    public static final int CARD_SIZE_RUN = 12;
    public static final int SEARCH_BEST_Y_DEPTH = 8;
    private static final int CARD_WIDTH = 52;
    public static final int CARD_HEIGHT = 74;
    public static final int SUIT_START_Y = 27;
    public static final int NUMBER_SPLIT_X = 8;

    public static void main(String[] args) {
        Consumer<String> logger = System.out::println;

        if (args.length == 0 || args[0].trim().isEmpty()) {
            logger.accept("You need to specify directory for searching.");
            return;
        }
        logger.accept("Parse png files in directory " + args[0]);
        try {
            streamFileNamesInDir(args[0]).stream()
                                         .map(path -> path + " - " + processFile(path))
                                         .forEach(logger);
        } catch (NoSuchFileException e) {
            logger.accept("Directory " + args[0] + " does not exist.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Path> streamFileNamesInDir(String dirName) throws IOException {
        try (Stream<Path> stream = Files.list(Paths.get(dirName))) {
            return stream.filter(file -> !Files.isDirectory(file) &&
                                         file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                         .toList();
        }
    }

    private static String processFile(Path pathToFile) {
        final StringBuilder result = new StringBuilder();
        try {
            final BufferedImage image = ImageIO.read(pathToFile.toFile());
            BufferedImage searchArea = image.getSubimage(SEARCH_AREA.x, SEARCH_AREA.y, SEARCH_AREA.width,
                                                         SEARCH_AREA.height);

            List<Rectangle> cardsToCheck = findPotentialCards(searchArea);
            cardsToCheck.forEach(card -> {
                BufferedImage numberImg = getFittedPicture(searchArea,
                                                           new Rectangle(card.x, card.y, 30,
                                                                         23));
                BufferedImage suitImg = getFittedPicture(searchArea,
                                                         new Rectangle(card.x + 16, card.y + SUIT_START_Y + 15,
                                                                       card.width - 17,
                                                                       card.height - SUIT_START_Y - 15));
                int redSign = isRed(suitImg) ? -1 : 1;
                List<Integer> numberHashes = getHashesList(1, numberImg,
                                                           new Point(NUMBER_SPLIT_X, numberImg.getHeight() / 2));
                List<Integer> suitHashes = getHashesList(redSign, suitImg,
                                                         new Point(suitImg.getWidth() / 2, suitImg.getHeight() / 2));
                String number = getMostFitted(hashOfNumbers, numberHashes);
                String suit = getMostFitted(hashOfSuits, suitHashes);
                if (number != null && suit != null) {
                    result.append(number);
                    result.append(suit);
                }

            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return result.isEmpty() ? "Found nothing" : result.toString();
    }

    private static BufferedImage getFittedPicture(BufferedImage searchArea, Rectangle bounds) {
        Point suitStart = fitBoundRightDown(searchArea, bounds.x, bounds.y,
                                            bounds.x + bounds.width, bounds.y + bounds.height,
                                            bounds.width, bounds.height);
        BufferedImage suitImg = searchArea.getSubimage(
                suitStart.x, suitStart.y,
                bounds.width + bounds.x - suitStart.x, bounds.height + bounds.y - suitStart.y);
        Point suitEnd = fitBoundLeftUp(suitImg);
        suitImg = suitImg.getSubimage(0, 0, suitEnd.x + 1, suitEnd.y + 1);
        return suitImg;
    }

    private static List<Integer> getHashesList(int redSign, BufferedImage numberImg, Point split) {
        return List.of(redSign * countPixels(numberImg.getSubimage(0, 0, split.x, split.y)), redSign * countPixels(
                numberImg.getSubimage(0, split.y, split.x, numberImg.getHeight() - split.y)), redSign * countPixels(
                numberImg.getSubimage(split.x, 0, numberImg.getWidth() - split.x, split.y)), redSign * countPixels(
                numberImg.getSubimage(split.x, split.y, numberImg.getWidth() - split.x,
                                      numberImg.getHeight() - split.y)));
    }

    private static String getMostFitted(Map<String, Set<List<Integer>>> hashesByKeys, List<Integer> hash) {
        return hashesByKeys.entrySet()
                           .stream()
                           .min(Comparator.comparing(entry -> entry.getValue().stream()
                                                                   .mapToDouble(nHash -> sqDist(nHash, hash))
                                                                   .min()
                                                                   .orElse(Double.MAX_VALUE)))
                           .map(Map.Entry::getKey)
                           .orElse(null);
    }

    // Always positive

    private static double sqDist(List<Integer> h1, List<Integer> h2) {
        int size = min(h1.size(), h2.size());
        double score = 0.0;
        for (int i = 0; i < size; ++i) {
            score += (h1.get(i) - h2.get(i)) * (h1.get(i) - h2.get(i));
        }
        return score;
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

    private static List<Rectangle> findPotentialCards(BufferedImage image) {
        final List<Rectangle> rectangles = new ArrayList<>();
        int w = image.getWidth() - CARD_WIDTH - 2;
        int h = image.getHeight() - CARD_HEIGHT - 2;
        int y = 0;
        while (y < h) {
            int x = 0;
            while (x < w) {
                if (isCardStripeHorizontal(image, x, y, CARD_WIDTH) &&
                    isCardStripeHorizontal(image, x, y + SUIT_START_Y + 1, CARD_WIDTH)) {
                    List<Point> cardsToAdd = findBestStartOfCards(image, y);
                    cardsToAdd.forEach(start -> {
                        start = fitBoundRightDown(image, start.x, start.y, start.x + CARD_SIZE_RUN,
                                                  start.y + CARD_SIZE_RUN, CARD_WIDTH + 1, CARD_HEIGHT + 1);
                        rectangles.add(new Rectangle(start.x, start.y, CARD_WIDTH, CARD_HEIGHT));
                    });
                    y += CARD_HEIGHT;
                    break;
                } else {
                    ++x;
                }
            }
            ++y;
        }
        return rectangles;
    }

    private static List<Point> findBestStartOfCards(BufferedImage img, int y) {
        List<Point> bestCardStars = null;
        for (int i = 0; i < SEARCH_BEST_Y_DEPTH; ++i) {
            List<Point> tmpCards = new ArrayList<>(10);
            int x = 0;
            while (x < img.getWidth()) {
                if (isCardStripeHorizontal(img, x, y + i, CARD_WIDTH) &&
                    isCardStripeHorizontal(img, x, y + i + SUIT_START_Y + 1, CARD_WIDTH) &&
                    isCardStripeVertical(img, x, y + i, CARD_HEIGHT + 1)) {
                    tmpCards.add(new Point(x, y + i));
                    x += CARD_WIDTH + 1;
                } else {
                    ++x;
                }
            }
            // If the same size, higher y is better
            if (bestCardStars == null || bestCardStars.size() <= tmpCards.size()) {
                bestCardStars = tmpCards;
            }
        }
        return bestCardStars;
    }


    // Search first not white stripe in horizontal and vertical diractions
    private static Point fitBoundRightDown(BufferedImage img, int x, int y, int w, int h, int stripeW, int stripeH) {
        for (; y < h; ++y) {
            if (!isCardStripeHorizontal(img, x, y, stripeW)) {
                --y;
                break;
            }
        }
        for (; x < w; ++x) {
            if (!isCardStripeVertical(img, x, y, stripeH)) {
                --x;
                break;
            }
        }
        return new Point(x + 1, y + 1);
    }

    private static Point fitBoundLeftUp(BufferedImage img) {
        int x = img.getWidth() - 1;
        int y = img.getHeight() - 1;
        for (; y >= 0; --y) {
            if (!isCardStripeHorizontal(img, 0, y, img.getWidth())) {
                break;
            }
        }
        for (; x >= 0; --x) {
            if (!isCardStripeVertical(img, x, 0, img.getHeight())) {
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
        return r1 >= RED_TONE && (r1 - g1) > TONE_SENSITIVITY && (r1 - b1) > TONE_SENSITIVITY &&
               abs(g1 - b1) <= TONE_SENSITIVITY;
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
}
