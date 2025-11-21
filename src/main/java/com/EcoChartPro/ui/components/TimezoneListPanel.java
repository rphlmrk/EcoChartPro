package com.EcoChartPro.ui.components;

import com.EcoChartPro.ui.home.theme.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.function.Consumer;

/**
 * A panel that displays a curated list of timezones for selection,
 * designed to be placed within a JPopupMenu.
 */
public class TimezoneListPanel extends JPanel {

    private record TimezoneEntry(String displayName, ZoneId zoneId, boolean isSeparator) {
        TimezoneEntry(String displayName, ZoneId zoneId) {
            this(displayName, zoneId, false);
        }
        TimezoneEntry() {
            this("", null, true);
        }
        @Override
        public String toString() {
            return displayName;
        }
    }

    private final JList<TimezoneEntry> timezoneList;
    private final DefaultListModel<TimezoneEntry> model;

    public TimezoneListPanel(ZoneId currentZoneId, Consumer<ZoneId> onSelectCallback) {
        super(new BorderLayout());
        setBackground(UIManager.getColor("PopupMenu.background"));

        model = new DefaultListModel<>();
        populateModel();

        timezoneList = new JList<>(model);
        timezoneList.setCellRenderer(new TimezoneCellRenderer());
        timezoneList.setBackground(UIManager.getColor("List.background"));
        timezoneList.setForeground(UIManager.getColor("List.foreground"));
        timezoneList.setSelectionBackground(UIManager.getColor("List.selectionBackground"));
        timezoneList.setSelectionForeground(UIManager.getColor("List.selectionForeground"));

        for (int i = 0; i < model.size(); i++) {
            if (model.getElementAt(i).zoneId() != null && model.getElementAt(i).zoneId().equals(currentZoneId)) {
                timezoneList.setSelectedIndex(i);
                break;
            }
        }

        timezoneList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                TimezoneEntry selected = timezoneList.getSelectedValue();
                if (selected != null && !selected.isSeparator() && selected.zoneId() != null) {
                    onSelectCallback.accept(selected.zoneId());
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(timezoneList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setPreferredSize(new Dimension(220, 350));
        add(scrollPane, BorderLayout.CENTER);
    }
    
    private void addSafeTimezone(DefaultListModel<TimezoneEntry> model, String displayName, String zoneIdString) {
        try {
            model.addElement(new TimezoneEntry(displayName, ZoneId.of(zoneIdString)));
        } catch (ZoneRulesException e) {
            System.err.println("Warning: Timezone ID not found in this JRE, skipping: " + zoneIdString);
        }
    }

    private void populateModel() {
        addSafeTimezone(model, "UTC", "UTC");
        addSafeTimezone(model, "Exchange", "UTC");
        addSafeTimezone(model, "(UTC-10) Honolulu", "Pacific/Honolulu");
        addSafeTimezone(model, "(UTC-8) Anchorage", "America/Anchorage");
        addSafeTimezone(model, "(UTC-8) Juneau", "America/Juneau");
        addSafeTimezone(model, "(UTC-7) Los Angeles", "America/Los_Angeles");
        addSafeTimezone(model, "(UTC-7) Phoenix", "America/Phoenix");
        addSafeTimezone(model, "(UTC-7) Vancouver", "America/Vancouver");
        addSafeTimezone(model, "(UTC-6) Denver", "America/Denver");
        addSafeTimezone(model, "(UTC-6) Mexico City", "America/Mexico_City");
        addSafeTimezone(model, "(UTC-6) San Salvador", "America/El_Salvador");
        addSafeTimezone(model, "(UTC-5) Bogota", "America/Bogota");
        addSafeTimezone(model, "(UTC-5) Chicago", "America/Chicago");
        addSafeTimezone(model, "(UTC-5) Lima", "America/Lima");
        addSafeTimezone(model, "(UTC-4) Caracas", "America/Caracas");
        addSafeTimezone(model, "(UTC-4) New York", "America/New_York");
        addSafeTimezone(model, "(UTC-4) Santiago", "America/Santiago");
        addSafeTimezone(model, "(UTC-4) Toronto", "America/Toronto");
        addSafeTimezone(model, "(UTC-3) Buenos Aires", "America/Buenos_Aires");
        addSafeTimezone(model, "(UTC-3) Sao Paulo", "America/Sao_Paulo");
        addSafeTimezone(model, "(UTC) Azores", "Atlantic/Azores");
        addSafeTimezone(model, "(UTC) Reykjavik", "Atlantic/Reykjavik");
        addSafeTimezone(model, "(UTC+1) Casablanca", "Africa/Casablanca");
        addSafeTimezone(model, "(UTC+1) Dublin", "Europe/Dublin");
        addSafeTimezone(model, "(UTC+1) Lagos", "Africa/Lagos");
        addSafeTimezone(model, "(UTC+1) Lisbon", "Europe/Lisbon");
        addSafeTimezone(model, "(UTC+1) London", "Europe/London");
        addSafeTimezone(model, "(UTC+1) Tunis", "Africa/Tunis");
        addSafeTimezone(model, "(UTC+2) Amsterdam", "Europe/Amsterdam");
        addSafeTimezone(model, "(UTC+2) Belgrade", "Europe/Belgrade");
        addSafeTimezone(model, "(UTC+2) Berlin", "Europe/Berlin");
        addSafeTimezone(model, "(UTC+2) Bratislava", "Europe/Bratislava");
        addSafeTimezone(model, "(UTC+2) Brussels", "Europe/Brussels");
        addSafeTimezone(model, "(UTC+2) Budapest", "Europe/Budapest");
        addSafeTimezone(model, "(UTC+2) Copenhagen", "Europe/Copenhagen");
        addSafeTimezone(model, "(UTC+2) Johannesburg", "Africa/Johannesburg");
        addSafeTimezone(model, "(UTC+2) Luxembourg", "Europe/Luxembourg");
        addSafeTimezone(model, "(UTC+2) Madrid", "Europe/Madrid");
        addSafeTimezone(model, "(UTC+2) Malta", "Europe/Malta");
        addSafeTimezone(model, "(UTC+2) Oslo", "Europe/Oslo");
        addSafeTimezone(model, "(UTC+2) Paris", "Europe/Paris");
        addSafeTimezone(model, "(UTC+2) Prague", "Europe/Prague");
        addSafeTimezone(model, "(UTC+2) Rome", "Europe/Rome");
        addSafeTimezone(model, "(UTC+2) Stockholm", "Europe/Stockholm");
        addSafeTimezone(model, "(UTC+2) Vienna", "Europe/Vienna");
        addSafeTimezone(model, "(UTC+2) Warsaw", "Europe/Warsaw");
        addSafeTimezone(model, "(UTC+2) Zurich", "Europe/Zurich");
        addSafeTimezone(model, "(UTC+3) Athens", "Europe/Athens");
        addSafeTimezone(model, "(UTC+3) Bahrain", "Asia/Bahrain");
        addSafeTimezone(model, "(UTC+3) Bucharest", "Europe/Bucharest");
        addSafeTimezone(model, "(UTC+3) Cairo", "Africa/Cairo");
        addSafeTimezone(model, "(UTC+3) Helsinki", "Europe/Helsinki");
        addSafeTimezone(model, "(UTC+3) Istanbul", "Europe/Istanbul");
        addSafeTimezone(model, "(UTC+3) Jerusalem", "Asia/Jerusalem");
        addSafeTimezone(model, "(UTC+3) Kuwait", "Asia/Kuwait");
        addSafeTimezone(model, "(UTC+3) Moscow", "Europe/Moscow");
        addSafeTimezone(model, "(UTC+3) Nairobi", "Africa/Nairobi");
        addSafeTimezone(model, "(UTC+3) Nicosia", "Asia/Nicosia");
        addSafeTimezone(model, "(UTC+3) Qatar", "Asia/Qatar");
        addSafeTimezone(model, "(UTC+3) Riga", "Europe/Riga");
        addSafeTimezone(model, "(UTC+3) Riyadh", "Asia/Riyadh");
        addSafeTimezone(model, "(UTC+3) Tallinn", "Europe/Tallinn");
        addSafeTimezone(model, "(UTC+3) Vilnius", "Europe/Vilnius");
        addSafeTimezone(model, "(UTC+3:30) Tehran", "Asia/Tehran");
        addSafeTimezone(model, "(UTC+4) Dubai", "Asia/Dubai");
        addSafeTimezone(model, "(UTC+4) Muscat", "Asia/Muscat");
        addSafeTimezone(model, "(UTC+4:30) Kabul", "Asia/Kabul");
        addSafeTimezone(model, "(UTC+5) Ashgabat", "Asia/Ashgabat");
        addSafeTimezone(model, "(UTC+5) Astana", "Asia/Almaty");
        addSafeTimezone(model, "(UTC+5) Karachi", "Asia/Karachi");
        addSafeTimezone(model, "(UTC+5:30) Colombo", "Asia/Colombo");
        addSafeTimezone(model, "(UTC+5:30) Kolkata", "Asia/Kolkata");
        addSafeTimezone(model, "(UTC+5:45) Kathmandu", "Asia/Kathmandu");
        addSafeTimezone(model, "(UTC+6) Dhaka", "Asia/Dhaka");
        addSafeTimezone(model, "(UTC+6:30) Yangon", "Asia/Yangon");
        addSafeTimezone(model, "(UTC+7) Bangkok", "Asia/Bangkok");
        addSafeTimezone(model, "(UTC+7) Ho Chi Minh", "Asia/Ho_Chi_Minh");
        addSafeTimezone(model, "(UTC+7) Jakarta", "Asia/Jakarta");
        addSafeTimezone(model, "(UTC+8) Chongqing", "Asia/Chongqing");
        addSafeTimezone(model, "(UTC+8) Hong Kong", "Asia/Hong_Kong");
        addSafeTimezone(model, "(UTC+8) Kuala Lumpur", "Asia/Kuala_Lumpur");
        addSafeTimezone(model, "(UTC+8) Manila", "Asia/Manila");
        addSafeTimezone(model, "(UTC+8) Perth", "Australia/Perth");
        addSafeTimezone(model, "(UTC+8) Shanghai", "Asia/Shanghai");
        addSafeTimezone(model, "(UTC+8) Singapore", "Asia/Singapore");
        addSafeTimezone(model, "(UTC+8) Taipei", "Asia/Taipei");
        addSafeTimezone(model, "(UTC+9) Seoul", "Asia/Seoul");
        addSafeTimezone(model, "(UTC+9) Tokyo", "Asia/Tokyo");
        addSafeTimezone(model, "(UTC+9:30) Adelaide", "Australia/Adelaide");
        addSafeTimezone(model, "(UTC+10) Brisbane", "Australia/Brisbane");
        addSafeTimezone(model, "(UTC+10) Sydney", "Australia/Sydney");
        addSafeTimezone(model, "(UTC+11) Norfolk Island", "Pacific/Norfolk");
        addSafeTimezone(model, "(UTC+12) New Zealand", "Pacific/Auckland");
        addSafeTimezone(model, "(UTC+12:45) Chatham Islands", "Pacific/Chatham");
        addSafeTimezone(model, "(UTC+13) Tokelau", "Pacific/Tokelau");
    }

    private static class TimezoneCellRenderer extends DefaultListCellRenderer {
        private final JSeparator separator = new JSeparator();
        private static final Icon CHECK_ICON = UITheme.getIcon(UITheme.Icons.CHECKMARK, 16, 16);
        private static final Icon EMPTY_ICON = new ImageIcon(new BufferedImage(CHECK_ICON.getIconWidth(), CHECK_ICON.getIconHeight(), BufferedImage.TYPE_INT_ARGB));

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            if (value instanceof TimezoneEntry entry && entry.isSeparator()) {
                return separator;
            }

            // Call the super method first to get a correctly styled JLabel
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
            label.setIconTextGap(10);

            // The super call already sets the correct background/foreground for selection.
            // We just need to manage the icon and font style.
            if (isSelected) {
                label.setIcon(CHECK_ICON);
                label.setFont(label.getFont().deriveFont(Font.BOLD));
            } else {
                label.setIcon(EMPTY_ICON);
                label.setFont(label.getFont().deriveFont(Font.PLAIN));
            }
            return label;
        }
    }
}