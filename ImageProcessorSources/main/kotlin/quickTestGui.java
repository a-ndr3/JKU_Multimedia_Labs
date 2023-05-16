import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

public class quickTestGui
{
    /*
        some tests of filters and combinations so far:
         brightnessHSV + sharpen => good result, may also add contrast

         negative saturation (< -100) could be useful for some filters
    */

    //TODO: change int filter strength to double ?
    //TODO: finish implementing RGB fields for additional data field from interface
    private JFrame frame;
    private JLabel imageLabel;
    private JComboBox<String> filterList;
    private JSlider filterStrength;
    private JSpinner filterStrengthSpinner;
    private JButton filterButton;
    private JButton uploadButton;
    private JButton revertButton;

    private JTextField redField;
    private JTextField greenField;
    private JTextField blueField;

    private BufferedImage originalImage;
    private BufferedImage processedImage;

    private Deque<BufferedImage> imageHistory;
    private FilterFactory filterFactory;
    public quickTestGui() {
        imageHistory = new LinkedList<>();
        prepareUI();
        filterFactory = new FilterFactory();
    }

    private void prepareUI() {
        frame = new JFrame("Image Filters Test");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);

        //Create
        imageLabel = new JLabel();
        filterList = new JComboBox<>(Arrays.stream(FilterTypes.values()).map(Enum::toString).toArray(String[]::new));
        filterStrength = new JSlider(-10000, 10000, 0); // -10k to 10k for test purposes
        filterStrengthSpinner = new JSpinner(new SpinnerNumberModel(0, -10000, 10000, 1));
        filterButton = new JButton("Apply Filter");
        uploadButton = new JButton("Upload Image");
        revertButton = new JButton("Revert Image");

        filterStrength.addChangeListener(e -> filterStrengthSpinner.setValue(filterStrength.getValue()));
        filterStrengthSpinner.addChangeListener(e -> filterStrength.setValue((Integer) filterStrengthSpinner.getValue()));

        redField = new JTextField(3);
        greenField = new JTextField(3);
        blueField = new JTextField(3);
        filterButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                imageHistory.push(copyImage(processedImage));
                FilterTypes filterType = FilterTypes.valueOf((String) filterList.getSelectedItem());
                IFilter filter = filterFactory.createFilter(filterType);
                processedImage = filter.apply(processedImage, filterStrength.getValue(), Collections.emptyList());
                // Display processedImage
                imageLabel.setIcon(new ImageIcon(processedImage));
                frame.pack();
            }
        });

        uploadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                FileNameExtensionFilter filter = new FileNameExtensionFilter("Image Files", "jpg", "png", "jpeg");
                fileChooser.setFileFilter(filter);
                int returnValue = fileChooser.showOpenDialog(null);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = fileChooser.getSelectedFile();
                    try {
                        originalImage = ImageIO.read(selectedFile);
                        processedImage = copyImage(originalImage);
                        imageLabel.setIcon(new ImageIcon(originalImage));
                        frame.pack();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });

        revertButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!imageHistory.isEmpty()) {
                    processedImage = imageHistory.pop();
                }
                else
                {
                    processedImage = originalImage;
                }
                imageLabel.setIcon(new ImageIcon(processedImage));
                frame.pack();
            }
        });

        //Layout
        JPanel controlPanel = new JPanel();
        controlPanel.add(uploadButton);
        controlPanel.add(new JLabel("Filter: "));
        controlPanel.add(filterList);
        controlPanel.add(new JLabel("Strength: "));
        controlPanel.add(filterStrength);
        controlPanel.add(filterStrengthSpinner);
        controlPanel.add(filterButton);
        controlPanel.add(revertButton);

        controlPanel.add(new JLabel("Red: "));
        controlPanel.add(redField);
        controlPanel.add(new JLabel("Green: "));
        controlPanel.add(greenField);
        controlPanel.add(new JLabel("Blue: "));
        controlPanel.add(blueField);

        frame.getContentPane().add(controlPanel, BorderLayout.NORTH);
        frame.getContentPane().add(new JScrollPane(imageLabel), BorderLayout.CENTER);
    }

    public void showUI() {
        frame.setVisible(true);
    }

    private BufferedImage copyImage(BufferedImage sourceImage) {
        BufferedImage copyOfImage = new BufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), sourceImage.getType());
        Graphics g = copyOfImage.createGraphics();
        g.drawImage(sourceImage, 0, 0, null);
        g.dispose();
        return copyOfImage;
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                quickTestGui app = new quickTestGui();
                app.showUI();
            }
        });
    }
}
