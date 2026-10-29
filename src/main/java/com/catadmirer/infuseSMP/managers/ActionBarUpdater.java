package com.catadmirer.infuseSMP.managers;

import com.catadmirer.infuseSMP.Infuse;
import com.catadmirer.infuseSMP.effects.InfuseEffect;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Composes and sends a single player's Infuse action bar. */
public final class ActionBarUpdater {
    private static final Key EFFECTS_FONT = Key.key("infuse", "effects");

    private final Infuse plugin;

    public ActionBarUpdater(Infuse plugin) {
        this.plugin = plugin;
    }

    public void update(Player player) {
        UUID uuid = player.getUniqueId();
        char placeholder = plugin.getMainConfig().emptyEffectIcon() ? '\uffff' : ' ';

        char leftIcon = placeholder;
        char rightIcon = placeholder;

        InfuseEffect effect = plugin.getDataManager().getEffect(uuid, "1");
        if (effect != null) leftIcon = effect.getIcon(player, "1");

        effect = plugin.getDataManager().getEffect(uuid, "2");
        if (effect != null) rightIcon = effect.getIcon(player, "2");

        player.sendActionBar(Component.text(leftIcon + " " + rightIcon).font(EFFECTS_FONT));
    }
}
