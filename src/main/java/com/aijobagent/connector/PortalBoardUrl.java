package com.aijobagent.connector;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public record PortalBoardUrl(String sourceId, String board, String postingId) {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    public String boardUrl() {
        String host = sourceId.equals("GREENHOUSE") ? "boards.greenhouse.io" : "jobs.lever.co";
        return "https://" + host + "/" + board;
    }

    public static Optional<PortalBoardUrl> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getRawFragment() != null) {
                return Optional.empty();
            }

            String host = uri.getHost();
            if (host == null) {
                return Optional.empty();
            }
            host = host.toLowerCase(Locale.ROOT);

            String sourceId;
            if (host.equals("boards.greenhouse.io") || host.equals("job-boards.greenhouse.io")) {
                sourceId = "GREENHOUSE";
            } else if (host.equals("jobs.lever.co")) {
                sourceId = "LEVER";
            } else {
                return Optional.empty();
            }

            List<String> segments = List.of(uri.getPath().split("/")).stream()
                    .filter(segment -> !segment.isBlank())
                    .toList();
            if (segments.isEmpty() || !IDENTIFIER.matcher(segments.getFirst()).matches()) {
                return Optional.empty();
            }

            String board = segments.getFirst();
            String postingId = null;
            if (segments.size() > 1) {
                if (sourceId.equals("GREENHOUSE")) {
                    if (segments.size() != 3 || !segments.get(1).equals("jobs")) {
                        return Optional.empty();
                    }
                    postingId = segments.get(2);
                } else {
                    if (segments.size() != 2) {
                        return Optional.empty();
                    }
                    postingId = segments.get(1);
                }
                if (!IDENTIFIER.matcher(postingId).matches()) {
                    return Optional.empty();
                }
            }

            return Optional.of(new PortalBoardUrl(sourceId, board, postingId));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
