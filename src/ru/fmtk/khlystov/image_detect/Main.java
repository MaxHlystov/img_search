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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.abs;
import static java.lang.Math.min;

public class Main {

    // sx,sy - bounds of the hash of the first part of picture
    // ex, ey - bounds of the hash of the second part of picture
    private static final Map<String, List<Set<Integer>>> hashOfNumbers = new HashMap<>();
    private static final Map<String, List<Set<Integer>>> boundsByNumber = new HashMap<>();
    private static final TreeMap<Integer, String> suitsByHash = new TreeMap<>();

    static {
        hashOfNumbers.put("A", List.of(Set.of(17, 9, -10, -11, -12, 12, -13, 13, -15, 15),
                                       Set.of(48, -42, -43, -44, -45, 45, 46, 47),
                                       Set.of(-114, 129, -115, 132, -118, -120, -121, 138, 125, 127)));
        hashOfNumbers.put("J", List.of(Set.of(0), Set.of(-24, 25, -27, 27), Set.of(-85, 85, 86, -88, -72, 73, 89)));
        hashOfNumbers.put("K", List.of(Set.of(-50, 50, 53, 54, -39, -43, 43), Set.of(-54, 54, 55, -44, -45, 45, -46),
                                       Set.of(-81, -82, 83, -85, -86, 85, -89, 92, 93, 94)));
        hashOfNumbers.put("Q", List.of(Set.of(-39, -40, 40, -41, -42, 41, 43), Set.of(-41, -42, 41, 42, 44),
                                       Set.of(162, -147, -148, -149, -150, -151, 156, 157, 158)));
        hashOfNumbers.put("10", List.of(Set.of(-49, 48, 49, -51, -55, -56, 56, 57, -46, -48, 47),
                                        Set.of(-33, 33, -36, -44, 44),
                                        Set.of(193, -179, 194, 197, -182, 215, -185, 185, 188, 190, -272)));
        hashOfNumbers.put("2", List.of(Set.of(32, -33, 33, -22, -23, -25, 25, 26),
                                       Set.of(48, -51, 51, 52, 54, -43, -45, -46, 46),
                                       Set.of(-81, 84, 85, -89, 125, -126, 94, -79, 126)));
        hashOfNumbers.put("3", List.of(Set.of(-37, 37, -27, -30, 30, 31), Set.of(39, -40, 40, -26, -28, -29, 28, 30),
                                       Set.of(-97, 148, 100, -149, 101, 150, -104, 106, -94)));
        hashOfNumbers.put("4", List.of(Set.of(-17, 17, -18, 18, -21, 21, -14, 13, -15, 15),
                                       Set.of(-33, 34, -36, 35, 36, -42, -43, 42, 43),
                                       Set.of(-118, -119, 119, -152, -105, 152, -154, 154, 107, -144, 127)));
        hashOfNumbers.put("5",
                          List.of(Set.of(-50, 51, -53, -54, 55, 57, 58, -62), Set.of(32, -26, -42, -27, -29, 29, 30),
                                  Set.of(-81, -83, 84, 85, -123, -77, 78, 79)));
        hashOfNumbers.put("6",
                          List.of(Set.of(-50, -51, 51, 52, 54, 56, 57, -47, -48), Set.of(48, 49, 50, 69, -44, -47, -48),
                                  Set.of(-84, 89, -91, 123, 92, 93, 125)));
        hashOfNumbers.put("7", List.of(Set.of(-33, -34, 21, -23, -24, 24, -25, 25, -26, 26, 31),
                                       Set.of(32, 33, -34, -35, 36, -38, 37, 38, -42, -45, -31),
                                       Set.of(-65, -82, -83, 68, -55, 57, -58, -59, 61, 95, -80)));
        hashOfNumbers.put("8", List.of(Set.of(-49, 49, -44, -45, -46, 46, 47), Set.of(-49, 48, -50, 50, 52, 53, -48),
                                       Set.of(98, -100, 100, 101, 104, -93, -94, 109, -95)));
        hashOfNumbers.put("9", List.of(Set.of(48, 49, 50, -56, -44, -45, 47, -48),
                                       Set.of(-34, -36, 37, 39, -41, 40, -58, 42),
                                       Set.of(-145, -98, 103, -105, -107, 107, 109)));


        suitsByHash.put(98, "c");
        suitsByHash.put(107, "c");
        suitsByHash.put(143, "c");

        suitsByHash.put(115, "s");
        suitsByHash.put(158, "s");

        suitsByHash.put(-92, "d");
        suitsByHash.put(-116, "d");

        suitsByHash.put(-141, "h");
        suitsByHash.put(-114, "h");
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
                                         .map(path -> path + " - " + processFile(logger, path))
                                         .forEach(logger);
        } catch (NoSuchFileException e) {
            logger.accept("Directory " + args[0] + " does not exist.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        logger.accept("=========================== hashes 1,2,3.. for numbers");
        boundsByNumber.entrySet().forEach(entry -> {
            String number = entry.getKey();
            String values = entry.getValue()
                                 .stream()
                                 .map(points -> points.stream()
                                                      .map(v -> v.toString())
                                                      .collect(Collectors.joining(", ", "Set.of(", ")")))
                                 .collect(Collectors.joining(",\n      "));
            logger.accept("hashOfNumbers.put(\"" + number + "\", List.of(\n" + values + "));");
        });
    }

    private static List<Path> streamFileNamesInDir(String dirName) throws IOException {
        try (Stream<Path> stream = Files.list(Paths.get(dirName))) {
            return stream.filter(file -> !Files.isDirectory(file) &&
                                         file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                         .toList();
        }
    }

    private static String processFile(Consumer<String> logger, Path pathToFile) {
        final String writeName = pathToFile.getFileName().toString();
        final String cards = writeName.substring(0, writeName.lastIndexOf('.'));
        final String cardsNorm = cards.replace("10", "1");
        final int realCardsCount = cardsNorm.length() / 2;
        final StringBuilder result = new StringBuilder();
        try {
            final BufferedImage image = ImageIO.read(pathToFile.toFile());
            BufferedImage searchArea = image.getSubimage(SEARCH_AREA.x, SEARCH_AREA.y, SEARCH_AREA.width,
                                                         SEARCH_AREA.height);
//            write(pathToFile, "t/" + writeName, searchArea);

            List<Rectangle> cardsToCheck = findPotentialCards(searchArea);
            final int[] i = new int[]{0};
            cardsToCheck.forEach(card -> {
                String realNumber = cardsNorm.substring(i[0] * 2, i[0] * 2 + 1);
                String realSuit = cardsNorm.substring(i[0] * 2 + 1, i[0] * 2 + 2);

                BufferedImage numberImg = searchArea.getSubimage(card.x, card.y, 30, 23);
                Point suitStart = fitBoundRightDown(searchArea, card.x, card.y + SUIT_START_Y, card.x + 12,
                                                    card.y + SUIT_START_Y + 21, 17, 17);
                if (suitStart == null) {
                    suitStart = new Point(card.x, card.y + SUIT_START_Y);
                }
                BufferedImage suitImg = searchArea.getSubimage(suitStart.x, suitStart.y, 16, 21);
                boolean isRed = isRed(suitImg);
                int ySplit = numberImg.getHeight() / 2;
                List<Integer> hashes = List.of(
                        (isRed ? -1 : 1) * countPixels(numberImg.getSubimage(0, 0, NUMBER_SPLIT_X, ySplit)),
                        (isRed ? -1 : 1) *
                        countPixels(numberImg.getSubimage(0, ySplit, NUMBER_SPLIT_X, numberImg.getHeight() - ySplit)),
                        (isRed ? -1 : 1) * countPixels(
                                numberImg.getSubimage(NUMBER_SPLIT_X, 0, numberImg.getWidth() - NUMBER_SPLIT_X,
                                                      numberImg.getHeight())));
                int numberHashesSize = hashes.size();
                int suitHash = countPixels(suitImg);

                String number = getMostFitted(hashOfNumbers, hashes);
                String suit = getMostFitted(suitsByHash, (isRed ? -1 : 1) * suitHash);
                if (number != null && suit != null) {
                    result.append(number);
                    result.append(suit);
                }
                logger.accept(
                        "Card: (" + card.x + ", " + card.y + ", " + card.width + ", " + card.height + ") = " + hashes +
                        "_" + (isRed ? 'R' : "B") + suitHash + "=" + realNumber + realSuit + " >>> " + number + suit);

                List<Set<Integer>> hashesToFill = boundsByNumber.computeIfAbsent(realNumber, k -> {
                    var values = new ArrayList<Set<Integer>>();
                    for (int j = 0; j < numberHashesSize; ++j) {
                        values.add(new HashSet<>());
                    }
                    return values;
                });
                for (int j = 0; j < numberHashesSize; ++j) {
                    hashesToFill.get(j).add(hashes.get(j));
                }

//                write(pathToFile, "t/" + "zN_" + realNumber + realSuit + "_" + writeName, numberImg);
//                write(pathToFile, "t/" + "zS_" + realSuit + realNumber + "_" + writeName, suitImg);
//                write(pathToFile, "t/" + "z_" + realNumber + realSuit + "_x" + card.x + 'y' + card.y + '_' + writeName, searchArea.getSubimage(card.x, card.y, CARD_WIDTH, CARD_HEIGHT));
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

    private static String getMostFitted(Map<String, List<Set<Integer>>> hashOfNumbers, List<Integer> hash) {
        List<String> numbers = hashOfNumbers.entrySet().stream().filter(entry -> {
            var hashes = entry.getValue();
            int size = min(hashes.size(), hash.size());
            for (int i = 0; i < size; ++i) {
                if (!hashes.get(i).contains(hash.get(i))) {
                    return false;
                }
            }
            return true;
        }).map(Map.Entry::getKey).toList();
        if (numbers.size() > 1) {
            System.out.println("*** For hash " + hash + " fits " + numbers);
        }
        return numbers.isEmpty() ? null : numbers.get(0);
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
                        rectangles.add(new Rectangle(start.x + 1, start.y + 1, CARD_WIDTH, CARD_HEIGHT));
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


    ///////////////////

    private static String toRGBString(int rgb) {
        int r1 = ((rgb >> 16) & 0xFF);
        int g1 = ((rgb >> 8) & 0xFF);
        int b1 = (rgb & 0xFF);
        return String.format("#%02X%02X%02X", r1, g1, b1);
    }

    private static void write(Path pathToFile, String name, BufferedImage searchArea) {
        Path write = pathToFile.getParent().resolve(name);
        try {
            ImageIO.write(searchArea, "png", write.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
