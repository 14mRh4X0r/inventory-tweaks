package invtweaks.forge;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;

/**
 * ModLoader entry point to load and configure the mod.
 *
 * @author Jimeo Wan
 *         <p/>
 *         Contact: jimeo.wan (at) gmail (dot) com
 *         Website: {@link http://wan.ka.free.fr/?invtweaks}
 *         Source code: {@link https://github.com/mkalam-alami/inventory-tweaks}
 *         License: MIT
 */
@Mod(modid = "inventorytweaks",
        dependencies = "required-after:FML@[5.0.0,);required-after:Forge@[7.7.0,)")
@NetworkMod(channels = {"InventoryTweaks"}, packetHandler = PacketHandler.class, connectionHandler = ConnectionHandler.class)
public class InvTweaksMod {
    @SidedProxy(clientSide = "invtweaks.forge.ClientProxy", serverSide = "invtweaks.forge.CommonProxy")
    public static CommonProxy proxy;

    @Mod.PreInit
    public void preInit(FMLPreInitializationEvent e) {
        proxy.preInit(e);
    }

    @Mod.Init
    public void init(FMLInitializationEvent e) {
        proxy.init(e);
    }

    @Mod.PostInit
    public void postInit(FMLPostInitializationEvent e) {
        proxy.postInit(e);
    }
}
