package org.rivierarobotics;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.InflaterInputStream;

import org.rivierarobotics.protos.Packet.Frame;
import org.rivierarobotics.protos.Packet.Signal;

import com.google.protobuf.ByteString;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;

public class VisionController {

    private static final Path ADDRESS_FILE =
            Paths.get(System.getProperty("user.home"), ".vision5818_address");
    private final NetworkManager network = new NetworkManager();
    private final FrameRequester requester = new FrameRequester(network);
    @FXML
    private ImageView imageView;
    @FXML
    private Label sourceText;
    @FXML
    private TextField address;
    private String originalSourceText;

    private void setSourceText(Source source) {
        sourceText.setText(originalSourceText + source.toString());
    }

    private void loadImage(Frame frame) {
        ByteString bytes = frame.getJpeg();
        try (InputStream in = bytes.newInput();
                InflaterInputStream inflater = new InflaterInputStream(in)) {
            // read jpeg from inflated stream
            Image image = new Image(inflater, 0, 0, true, true);
            Platform.runLater(() -> {
                imageView.setImage(image);
                imageView.getParent().applyCss();
                imageView.getParent().layout();
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void initialize() {
        originalSourceText = sourceText.getText();
        setSourceText(requester.getSource());
        requester.setFrameCallback(this::loadImage);
        try {
            if (Files.exists(ADDRESS_FILE)) {
                String s = StandardCharsets.UTF_8
                        .decode(ByteBuffer
                                .wrap(Files.readAllBytes(ADDRESS_FILE)))
                        .toString();
                setAddress(s, false);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // resize image view to fit parent
        imageView.fitHeightProperty()
                .bind(((Pane) imageView.getParent()).heightProperty());
    }

    private void setAddress(String s, boolean fromField) {
        String[] parts = s.split(":", 2);
        int port;
        try {
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException nfe) {
            return;
        }
        network.setAddr(parts[0]);
        network.setPort(port);

        if (fromField) {
            try {
                Files.write(ADDRESS_FILE, s.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            address.setText(s);
        }
    }

    @FXML
    public void switchSource() {
        requester.setSource(requester.getSource().other());
        setSourceText(requester.getSource());
    }

    @FXML
    public void switchFeed() {
        network.sendMessage(
                Signal.newBuilder().setType(Signal.Type.SWITCH_FEED).build());
    }

    @FXML
    public void onAddressSet() {
        setAddress(address.getText(), true);
    }
}
