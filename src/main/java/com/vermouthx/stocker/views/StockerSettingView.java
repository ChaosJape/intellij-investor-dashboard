package com.vermouthx.stocker.views;

import com.vermouthx.stocker.enums.StockerQuoteColorPattern;
import com.vermouthx.stocker.enums.StockerQuoteProvider;

import javax.swing.*;
import java.util.Objects;

public class StockerSettingView {
    private JPanel mPane;
    private JRadioButton rbRedUpPattern;
    private JRadioButton rbGreenUpPattern;
    private JComboBox<String> cbProvider;

    public JPanel getContent() {
        for (StockerQuoteProvider value : StockerQuoteProvider.values()) {
            cbProvider.addItem(value.getTitle());
        }
        return mPane;
    }

    public StockerQuoteProvider getSelectedQuoteProvider() {
        String title = (String) Objects.requireNonNull(cbProvider.getSelectedItem());
        if (StockerQuoteProvider.SINA.getTitle().equals(title)) {
            return StockerQuoteProvider.SINA;
        }
        return StockerQuoteProvider.TENCENT;
    }

    public StockerQuoteColorPattern getSelectedQuoteColorPattern() {
        if (rbRedUpPattern.isSelected()) {
            return StockerQuoteColorPattern.RED_UP_GREEN_DOWN;
        }
        return StockerQuoteColorPattern.GREEN_UP_RED_DOWN;
    }

    public void resetQuoteProvider(StockerQuoteProvider quoteProvider) {
        cbProvider.setSelectedItem(quoteProvider.getTitle());
    }

    public void resetQuoteColorPattern(StockerQuoteColorPattern colorPattern) {
        if (colorPattern == StockerQuoteColorPattern.RED_UP_GREEN_DOWN) {
            rbRedUpPattern.setSelected(true);
        } else {
            rbGreenUpPattern.setSelected(true);
        }
    }

}
