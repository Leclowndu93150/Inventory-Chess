package eu.pb4.sgui;

import net.fabricmc.api.ModInitializer;

public class SGuiFabricMod implements ModInitializer {

    @Override
    public void onInitialize(){
        SGuiMod.initialize();
    }
}