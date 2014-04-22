package jp.co.webnium.yabumi;

import android.net.Uri;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Image meta data class for yabumi.cc
 */
public class Image {
    final static private Pattern PATTERN_IMAGE_PATH = Pattern.compile("^/([0-9a-f]+)\\.(png|jpg|svg|gif|pdf)$");
    final static public String[] AVAILABLE_CONTENT_TYPES = {"image/png", "image/jpeg", "image/svg+xml", "image/gif", "application/pdf"};
    /**
     * Id of the image.
     */
    public String id;

    /**
     * PIN to modify image.
     */
    public String pin;

    /**
     * Extension of the image.
     */
    public String extension;

    /**
     *  Create image object from uri
     * @param uri URI to parse for image instance creation
     * @return an Image instance
     */
    static public Image fromUri(Uri uri) {
       Image image = new Image();

        Matcher matcher = PATTERN_IMAGE_PATH.matcher(uri.getPath());
        if (!matcher.find()) {
            return image;
        }

        image.id = matcher.group(1);
        image.pin = uri.getQueryParameter("pin");
        image.extension = matcher.group(2);
        return image;
    }

    /**
     * Has owner ship of this image or not.
     * @return True if the image is owned by the device.
     */
    public boolean isOwned() {
        return pin != null;
    }

    /**
     * Check this image is available or not.
     * @return True if the image is available.
     */
    public boolean isAvailable() {
        return id != null;
    }

    /**
     * Get image filename.
     * @return filename of this image.
     */
    public String getFilename() {
        assert isAvailable();
        return id + "." + extension;
    }

    /**
     * Get URL of the image
     *
     * @return URL of the image.
     */
    public String getUrl() {
        return "https://yabumi.cc/" + getFilename();
    }
}
