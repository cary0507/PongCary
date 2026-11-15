/*
 * Author: Cary
 * Description: A Pong game where 2 players can play on the same keyboard.
 * Player wins if the other player's paddle missed the ball
 * */

package csta.ibm.pong;  // This can access package-private classes like _WinDialog

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;

/**
 * <h1>Pong Game</h1>
 * <p>The main class for the Pong game. It extends the Game class and implements the game logic, including player
 * controls, ball movement, collision detection, and score tracking.</p>
 *
 * @author Cary
 * @since 2026-10-28
 * */
public class Pong extends Game {
    // SFX file paths
    private final String BOUNCE_SOUND_FILE = "/sfx/tung.wav";
    private final String PHANTOM_DISAPPEAR_SOUND_FILE = "/sfx/sahur.wav";
    private final String POWER_UP_SFX_FILE = "/sfx/tung sahur.wav";
    private final String SCORING_SFX_FILE = "/sfx/scoring.wav";
    private final String GAME_OVER_SFX_FILE = "/sfx/game over.wav";
    // Image file paths
    private final String POWER_UP_IMG_FILE = "/img/power up1.png";

    // Game objects
    private Ball ball;
    // Ball phantoms that only bounce to paddles once, they are slower than normal balls
    private ArrayList<Ball> ballPhantoms = new ArrayList<>();
    private Paddle player1;  // Left paddle
    private Paddle player2;  // Right paddle
    // There should be only one phantom power up appearing on the screen
    private PhantomPowerUp phantomPowerUp;
    private JLabel scores;  // Display score
    private JLabel readyMsg;

    // Custom keybind
    private boolean aKeyPressed = false;
    private boolean kKeyPressed = false;

    // Game pausing
    private static boolean gettingReady = false;
    private long readyStartTime;
    private final long READY_DURATION = 3000;

    // Base resolution for the game
    private final int RES_WIDTH = 600;
    private final int RES_HEIGHT = 420;
    private double scaleFactor = 1.0;  // Scale factor for the game objects based on the window size
    private int CENTER_X;
    private int CENTER_Y;

    // First player reaching this scores wins
    private final static int WIN_SCORE = 6;
    private int serveDir = 1;
    private int p1Score = 0;
    private int p2Score = 0;

    public Pong() {
        super();
        super.setResizable(false);  // Disable resizing
    }

    /**
     * Quick way to calculate the left or top position from a pivot
     *
     * @param pivot the component vector of the pivot point (x or y)
     * @param size the size of the object
     * @return the left or top position of that object offsetting from the pivot
     * */
    private static int pivotOffset(int pivot, int size) {
        return pivot - size / 2;
    }

    /**
     * Calculate the scale factor based on the current window size and the base resolution. The scale factor is used to
     * scale the size and speed of game objects to maintain consistent gameplay across different window sizes.
     * The scale factor is the minimum of the width scale and height scale, ensuring that the game objects fit within
     * the window without distortion.
     *
     * @return the scale factor as a floating number
     * */
    private double calculateScaleFactor() {
        double widthScale = (double) getFieldWidth() / RES_WIDTH;
        double heightScale = (double) getFieldHeight() / RES_HEIGHT;
        return Math.min(widthScale, heightScale);
    }

    /**
     * Used to create a sound file from Google Classroom example
     * Used AI to help debug my file path issue, and it turns out the file path is wrong. I need to use getResource()
     * to get the correct path.
     *
     * @param fileName the file path from the source root starting with a "/"
     */
    public static void initSound(String fileName) throws MalformedURLException, LineUnavailableException,
            UnsupportedAudioFileException, IOException {
        // System.out.println(Pong.class.getResource(fileName));
        File url = new File(fileName);
        Clip clip = AudioSystem.getClip();

        // Uses the class's resource path instead of system path
        AudioInputStream ais = AudioSystem.getAudioInputStream(
                Pong.class.getResource(fileName)
        );
        clip.open(ais);
        clip.start();
    }

    /**
     * Method to play sound effect
     *
     * @param fileName the file path from the source root starting with a "/"
     */
    public void playSound(String fileName){
        try {
            initSound(fileName);
        } catch (LineUnavailableException | UnsupportedAudioFileException
                 | IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Converts path to BufferedImage
     * <a href="https://www.youtube.com/watch?v=oPzPpUcDiYY&list=PL_QPQmz5C6WUF-pOQDsbsKbaBZqXj4qSq&index=8">
     *     Reference
     * </a>
     *
     * @param path the path to an image (Must start with a "/")
     * @return the BufferedImage, if exists
     * */
    public static BufferedImage pathToImage(String path) {
        BufferedImage img;
        try {
            img = ImageIO.read(Pong.class.getResourceAsStream(path));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return img;
    }

//    /**
//     * Pause the game for 3 seconds and show the ready text to make the players get ready
//     * */
//    public void gameReady() {
//        gettingReady = true;
//        readyStartTime = System.currentTimeMillis();
//
//        readyMsg.setVisible(true);
//        stopGame();
//        repaint();
//    }

    public static boolean inPause() {
        return gettingReady;
    }

    /**
     * Reset the ball to the center of the screen, and reset the paddles to the edges of the screen
     * Paused the game for 3 seconds for the players to be ready. Uses proportions of the screen size to set the
     * positions of the ball and paddles, so it works for any screen size.
     * */
    public void reset() {
        // Text setup
        int fontSize = (int) (16 * scaleFactor);

        int scoresWidth = (int) (300 * scaleFactor);
        int scoresHeight = (int) (50 * scaleFactor);
        int scoreLeft = pivotOffset(CENTER_X, scoresWidth);
        scores.setFont(new Font("Arial", Font.PLAIN, fontSize));
        scores.setForeground(Color.WHITE);
        scores.setBounds(scoreLeft, 0, scoresWidth, scoresHeight);

        int readyWidth = (int) (200 * scaleFactor);
        int readyHeight = (int) (50 * scaleFactor);
        int readyLeft = pivotOffset(CENTER_X, readyWidth);
        int readyTop = pivotOffset(CENTER_Y - (int) (40 * scaleFactor), readyHeight);
        readyMsg.setFont(new Font("Arial", Font.BOLD, fontSize));
        readyMsg.setForeground(Color.YELLOW);
        readyMsg.setBounds(readyLeft, readyTop, readyWidth, readyHeight);

        // Ball setup
        int ballRadius = (int) (10 * scaleFactor);  // Scale the ball size based on the window size
        int ballLeft = pivotOffset(CENTER_X, ballRadius);  // Pivot ball to the screen center
        int ballTop = pivotOffset(CENTER_Y, ballRadius);
        ball.setSize(ballRadius, ballRadius);
        ball.setX(ballLeft); ball.setY(ballTop);
        int xSpeed = (int) (5 * scaleFactor);  // Magnitude of the ball's x velocity
        ball.setVelocity(serveDir * xSpeed, 0);  // Apply direction to the x speed
        // Paddles setup
        int initialPaddleSpeed = (int) (5 * scaleFactor);  // Scale the paddle speed based on the window size
        int paddleWidth = (int) (10 * scaleFactor);
        int paddleHeight = (int) (100 * scaleFactor);
        int paddleTop = pivotOffset(CENTER_Y, paddleHeight);
        int paddleXOffset = (int) (20 * scaleFactor);  // Distance away from screen bounds
        // Player 1
        player1.setSize(paddleWidth, paddleHeight);
        player1.setX(paddleXOffset); player1.setY(paddleTop);
        player1.setYSpeed(initialPaddleSpeed);
        // Player 2
        int rPaddleLeft = getFieldWidth() - paddleWidth - paddleXOffset;
        player2.setSize(paddleWidth, paddleHeight);
        player2.setX(rPaddleLeft); player2.setY(paddleTop);
        player2.setYSpeed(initialPaddleSpeed);
        // Phantom array lists
        resetBallPhantoms();
        // Phantom power up possible locations
        int lowerBoundX = getFieldWidth() * 7 / 16;
        int upperBoundX = getFieldWidth() * 9 / 16;
        int lowerBoundY = getFieldHeight() / 4;
        int upperBoundY = getFieldHeight() * 3 / 4;
        phantomPowerUp.setPossibleZone(
                lowerBoundX, lowerBoundY, upperBoundX, upperBoundY
        );
        // Get ready
        gettingReady = true;
        readyStartTime = System.currentTimeMillis();
//        gameReady();
//        try {
//            gameReady();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
    }

    /**
     * Override p1Wins so it quits the game after pressing the OK button
     * */
    @Override
    public void p1Wins() {
        _WinDialog d = new _WinDialog(this, "Player 1 Wins!");
        d.ok.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.exit(1);  // When OK button pressed, quit the game
                setVisible(false);
            }
        });
        d.setVisible(true);
    }

    /**
     * Override p2Wins so it quits the game after pressing the OK button
     * */
    @Override
    public void p2Wins() {
        _WinDialog d = new _WinDialog(this, "Player 2 Wins!");
        d.ok.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.exit(1);
                setVisible(false);
            }
        });
        d.setVisible(true);
    }

    /**
     * Initialize all objects and call resetPosition to set the initial positions of the ball and paddles.
     * Also sets up the score display. The ball will have a random serve direction at the start of the program
     * */
    @Override
    public void setup() {
        setDelay(10);
        scaleFactor = calculateScaleFactor();
        CENTER_X = getFieldWidth() / 2;
        CENTER_Y = getFieldHeight() / 2;

        // Add key press events
        addKeyPress();

        // Text setup
        scores = new JLabel();
        scores.setHorizontalAlignment(SwingConstants.CENTER);
        add(scores);  // Add to board

        readyMsg = new JLabel("GET READY");
        readyMsg.setHorizontalAlignment(SwingConstants.CENTER);
        readyMsg.setVisible(false);
        add(readyMsg);

        // Initialize game objects
        ball = new Ball();
        double serveChance = Math.random();
        if (serveChance > 0.5) {
            serveDir = 1;
        } else {
            serveDir = -1;
        }
        add(ball);

        player1 = new Paddle();
        add(player1);
        player2 = new Paddle();
        add(player2);

        phantomPowerUp = new PhantomPowerUp();
        BufferedImage phantomImg = pathToImage(POWER_UP_IMG_FILE);
        phantomPowerUp.setImg(phantomImg);  // Set the image for power up
        int powerUpSize = (int) (64 * scaleFactor);  // Scale the power up size based on the window size
        phantomPowerUp.setSize(powerUpSize, powerUpSize);  // Match size with image
        add(phantomPowerUp);

        // Setup locations
        reset();
    }

    /**
     * Reset the ball phantoms by removing them from the game and clearing the list. This is called when a player scores
     * to ensure that any existing phantom balls are removed from the game before the next round starts
     * */
    private void resetBallPhantoms() {
        for (Ball phantom : ballPhantoms) {
            remove(phantom);  // Remove from the game
        }
        ballPhantoms.clear();  // Clear the list
    }

    /**
     * Check the collision of the ball with the phantom power up. If the ball collides with the power up, a phantom ball
     * is spawned, and the power up disappears. The phantom ball will have the same size as the main ball, but will move
     * slower and will disappear after hitting a paddle once. The power up will reappear after a random interval.
     * */
    private void powerUpCollision() {
        if (phantomPowerUp.collides(ball)) {
            Ball phantom = phantomPowerUp.spawnPhantom(ball);
            ballPhantoms.add(phantom);
            add(phantom);  // Add the phantom to the game
        }
    }

    /**
     * Check the collision of phantoms
     * */
    private void phantomCollision() {
        for (int i = ballPhantoms.size() - 1; i >= 0; i--) {  // Reverse loop to avoid ConcurrentModificationException
            // when removing phantoms
            Ball phantom = ballPhantoms.get(i);
            // Top & bottom bounds
            if (phantom.getY() <= 0 || phantom.getY() + phantom.getHeight() >= getFieldHeight()) {
                phantom.bounceY();
                playSound(BOUNCE_SOUND_FILE);
            }
            // Disappear after hitting the paddles
            if (phantom.collides(player1) || phantom.collides(player2)) {
                ballPhantoms.remove(phantom);
                remove(phantom);  // Remove from the game
                playSound(PHANTOM_DISAPPEAR_SOUND_FILE);
            }
        }
    }

    /**
     * Check the collision of ball object with other game objects such as the paddle that make the ball bounce back. If
     * the ball collides with the top or bottom bounds, it will bounce back. If the ball collides with a paddle, it will
     * bounce back and adjust its vertical velocity based on the paddle's vertical velocity. The ball will also play a
     * bounce sound effect when it collides with a paddle or the top/bottom bounds.
     * */
    private void ballBounce() {
        // Top & bottom bounds
        if (ball.getY() <= 0 || ball.getY() + ball.getHeight() >= getFieldHeight()) {
            ball.bounceY();
            playSound(BOUNCE_SOUND_FILE);
        }

        // Ball colliding with paddle
        if (ball.collides(player1)) {
            ball.bouncePaddle(player1.getDY());
            // Wrap the ball outside the paddle
            ball.setX(player1.getX() + player1.getWidth());
            playSound(BOUNCE_SOUND_FILE);
        }
        if (ball.collides(player2)) {
            ball.bouncePaddle(player2.getDY());
            ball.setX(player2.getX() - player2.getWidth());
            playSound(BOUNCE_SOUND_FILE);
        }
    }

    /**
     * When p1 scored, increase their score and reward them to have the next serve, and plays sound effect
     *
     * @param boundDir the bound's direction relating to the center of the screen. 0 if not out of bound
     * */
    private void playerScored(int boundDir) {
        if (boundDir == 0)
            return;
        if (boundDir == 1)   // Out of right bound, p1 scored
            p1Score++;
        else  // Out of left bound, p2 scored
            p2Score++;
        serveDir = boundDir;  // Reward them by serving the ball in p2's direction
        playSound(SCORING_SFX_FILE);  // Celebration sfx
        reset();
    }

    /**
     * Check the ball is out of which bound. -1 for left bound. 1 for right bound. 0 if not out of bound yet
     *
     * @return An integer represents the direction of the bound that the ball or phantom gets out of.
     * */
    private int outOfBoundsDir() {
        // Check for phantoms
        for (int i = ballPhantoms.size() -1; i >= 0; i--) {
            Ball phantom = ballPhantoms.get(i);
            // Out of left bound
            if (phantom.getX() <= 0)
                return -1;
            // Out of right bound
            if (phantom.getX() + phantom.getWidth() >= getFieldWidth())
                return 1;
        }
        // Check for ball separately because ballPhantoms might be empty
        if (ball.getX() <= 0) return -1;
        if (ball.getX() + ball.getWidth() >= getFieldWidth()) return 1;
        return 0;
    }

    /**
     * Check for wining conditions with all types of balls
     * */
    private void checkWin() {
        // Check if any player won
        if (p1Score >= WIN_SCORE) {
            stopGame();  // Pause the game
            p1Wins();
            playSound(GAME_OVER_SFX_FILE);
        }
        if (p2Score >= WIN_SCORE) {
            stopGame();
            p2Wins();
            playSound(GAME_OVER_SFX_FILE);
        }
    }

    /**
     * Add key press events to the Pong game, called by setup
     * Instead of pressing z/n to go up, x/m to go down, modify it to press a/k to go up, and z/m to go down
     * Resources found in Google Classroom
     * */
    public void addKeyPress() {
        // Added A key and Z key to move up
        addKeyListener(
                new KeyListener() {
                    public void keyTyped(KeyEvent e) {}

                    public void keyPressed(KeyEvent e) {
                        char pressed = Character.toUpperCase(e.getKeyChar());  // Case-insensitive
                        switch (pressed) {
                            case 'A':
                                aKeyPressed = true;
                                break;
                            case 'K':
                                kKeyPressed = true;
                                break;
                        }
                    }

                    public void keyReleased(KeyEvent e) {
                        char released = Character.toUpperCase(e.getKeyChar());  // Case-insensitive
                        switch (released) {
                            case 'A':
                                aKeyPressed = false;
                                break;
                            case 'K':
                                kKeyPressed = false;
                                break;
                        }
                    }
                }
        );
    }

    /**
     * Check keypress for both players to move each paddle accordingly
     * */
    private void checkKeypress() {
        // Player 1 keypress
        if (aKeyPressed) {
            if (player1.getY() <= 0) {  // Stop moving and restrain to the top of the screen
                player1.setY(0);
                player1.atRest();  // Stop moving
            } else {
                player1.moveUp();
            }
        } else if (ZKeyPressed()) {
            if (player1.getY() + player1.getHeight() >= getFieldHeight()) {  // Restrain to the bottom
                player1.setY(getFieldHeight() - player1.getHeight());
                player1.atRest();
            } else {
                player1.moveDown();
            }
        } else {  // None of z or x key is pressed
            player1.atRest();
        }
        // Player 2 keypress
        if (kKeyPressed) {
            if (player2.getY() <= 0) {
                player2.setY(0);
                player2.atRest();
            } else {
                player2.moveUp();
            }
        } else if (MKeyPressed()) {
            if (player2.getY() + player2.getHeight() >= getFieldHeight()) {
                player2.setY(getFieldHeight() - player2.getHeight());
                player2.atRest();
            } else   {
                player2.moveDown();
            }
        } else {
            player2.atRest();
        }
    }

    /**
     * The main loop of the game. It updates the score display, checks for collisions and out-of-bounds conditions,
     * handles player input, and updates the positions of the ball and paddles accordingly.
     * */
    @Override
    public void act() {
        // Display text and repaint the canva
        scores.setText("Player 1   " + p1Score + " | " + p2Score + "   Player 2");
        // Wait 3 seconds before starting the round
        if (gettingReady) {
            long curTime = System.currentTimeMillis();
            long timePassed = curTime - readyStartTime;
            readyMsg.setVisible(true);
            if (timePassed >= READY_DURATION) {
                readyMsg.setVisible(false);
                gettingReady = false;
            }
        } else {
            repaint();
            checkKeypress();
            // Collisions
            powerUpCollision();
            ballBounce();
            phantomCollision();
            // Check for scoring
            playerScored(outOfBoundsDir());
            // Winning conditions
            checkWin();
            // System.out.println(phantomPowerUp.getX() + " " + phantomPowerUp.getY());
        }
    }

    public static void main(String[] args) {
        // Instructions before the game starts
        String instructions = "WELCOME TO PONG!\n\n" +
                "Player 1 (Left Paddle):\n" +
                "A - Move Up\n" +
                "Z - Move Down\n\n" +
                "Player 2 (Right Paddle):\n" +
                "K - Move Up\n" +
                "M - Move Down\n\n" +
                "First player to reach" + WIN_SCORE + "points wins!\n\n" +
                "Click OK or close this window to start the game.";
        // Display instructions
        JOptionPane.showMessageDialog(
                null,
                instructions,
                "How to Play",
                JOptionPane.INFORMATION_MESSAGE
        );
        // Initialize the Game and game screen
        Pong p = new Pong();
        p.resize(1200, 840);
        p.setVisible(true);
        p.initComponents();
    }
}
