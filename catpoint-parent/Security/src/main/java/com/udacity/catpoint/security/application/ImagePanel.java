package com.udacity.catpoint.security.application;

import com.udacity.catpoint.security.data.AlarmStatus;
import com.udacity.catpoint.security.service.SecurityService;
import com.udacity.catpoint.security.service.StyleService;
import net.miginfocom.swing.MigLayout;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Panel containing the 'camera' output. Allows users to 'refresh' the camera
 * by uploading their own picture, and 'scan' the picture, sending it for image analysis
 */
@SuppressFBWarnings({"EI_EXPOSE_REP2","CT_CONSTRUCTOR_THROW"})
public class ImagePanel extends JPanel implements StatusListener {

    private final SecurityService securityService;

    private JLabel cameraHeader;
    private JLabel cameraLabel;
    private BufferedImage currentCameraImage;

    private static final int IMAGE_WIDTH = 300;
    private static final int IMAGE_HEIGHT = 225;

    public ImagePanel(SecurityService securityService) {
        super();
        setLayout(new MigLayout());

        this.securityService = securityService;
        securityService.addStatusListener(this);

        cameraHeader = new JLabel("Camera Feed");
        cameraHeader.setFont(StyleService.HEADING_FONT);

        cameraLabel = new JLabel();
        cameraLabel.setBackground(Color.WHITE);
        cameraLabel.setPreferredSize(new Dimension(IMAGE_WIDTH, IMAGE_HEIGHT));
        cameraLabel.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));

        JButton addPictureButton = new JButton("Refresh Camera");

        addPictureButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setCurrentDirectory(new File("."));
            chooser.setDialogTitle("Select Picture");
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }

            try {
                currentCameraImage = ImageIO.read(chooser.getSelectedFile());

                Image tmp = new ImageIcon(currentCameraImage).getImage();

                cameraLabel.setIcon(
                        new ImageIcon(tmp.getScaledInstance(
                                IMAGE_WIDTH,
                                IMAGE_HEIGHT,
                                Image.SCALE_SMOOTH))
                );

            } catch (Exception e1) {
                JOptionPane.showMessageDialog(null, "Invalid image selected.");
            }

            repaint();
        });

        JButton scanPictureButton = new JButton("Scan Picture");

        scanPictureButton.addActionListener(e -> {
            if (currentCameraImage != null) {
                securityService.processImage(currentCameraImage);
            }
        });

        add(cameraHeader, "span 3, wrap");
        add(cameraLabel, "span 3, wrap");
        add(addPictureButton);
        add(scanPictureButton);
    }

    @Override
    public void notify(AlarmStatus status) {
        // behavior not needed
    }

    @Override
    public void catDetected(boolean catDetected) {
        if (catDetected) {
            cameraHeader.setText("DANGER - CAT DETECTED");
        } else {
            cameraHeader.setText("Camera Feed - No Cats Detected");
        }
    }

    @Override
    public void sensorStatusChanged() {
        // behavior not needed
    }
}
