package ua.naiksoftware.stomp.pathmatcher;

import android.annotation.SuppressLint;

import java.util.Objects;

import ua.naiksoftware.stomp.dto.StompHeader;
import ua.naiksoftware.stomp.dto.StompMessage;

public class SubscriptionPathMatcher implements PathMatcher {

    @SuppressLint("NewApi")
    @Override
    public boolean matches(String path, StompMessage msg) {
        String subscription = msg.findHeader(StompHeader.SUBSCRIPTION);
        if (Objects.isNull(path) || Objects.isNull(subscription))
            return false;

        return subscription.equals(path);
    }
}
