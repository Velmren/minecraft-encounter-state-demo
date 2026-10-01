package dev.velmren.encounter.paper;

import dev.velmren.encounter.core.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.boss.*;
import java.util.*;

/** One isolated, rebuildable demonstration arena. All world mutations stay in its own world. */
final class ObservatoryArena implements Listener {
    static final String WORLD = "encounter_observatory";
    private final PaperEncounterPlugin plugin;
    private final EncounterEngine engine;
    private final NamespacedKey guardKey;
    private final Set<UUID> guards = new HashSet<>();
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> arenaDeaths = new HashSet<>();
    private final Set<Integer> relays = new HashSet<>();
    private final BossBar hud = Bukkit.createBossBar("Observatory · ready", BarColor.BLUE, BarStyle.SEGMENTED_10);
    private World world;
    private int clock;
    private int holdClock;
    private int waveClock;
    private boolean rewarded;
    private int generation;
    private static final int[][] RELAYS = {{-10, 0}, {10, 0}};

    ObservatoryArena(PaperEncounterPlugin plugin, EncounterEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
        guardKey = new NamespacedKey(plugin, "encounter_guard");
    }

    @SuppressWarnings("removal") // Paper 1.21.11 renamed rules for its future version; these are its compatibility keys.
    void prepare() {
        WorldCreator creator = new WorldCreator(WORLD).type(WorldType.FLAT);
        creator.generatorSettings("{\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1}],\"biome\":\"minecraft:plains\"}");
        world = Bukkit.createWorld(creator);
        if (world == null) throw new IllegalStateException("Cannot create observatory world");
        world.setTime(12500);
        world.setStorm(false);
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setSpawnLocation(0, 65, 17);
        cleanup();
        build();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void block(int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material, false);
    }

    private void build() {
        // Original procedural scene, no imported map or resource pack.
        for (int x = -20; x <= 20; x++) for (int z = -22; z <= 22; z++) {
            for (int y = 64; y <= 78; y++) block(x, y, z, Material.AIR);
            block(x, 63, z, Material.DEEPSLATE_TILES);
            block(x, 64, z, (Math.abs(x) == 20 || Math.abs(z) == 22)
                    ? Material.POLISHED_BLACKSTONE_BRICKS : ((x + z) % 7 == 0
                    ? Material.CRACKED_STONE_BRICKS : Material.STONE_BRICKS));
            if (Math.abs(x) == 20 || Math.abs(z) == 22) block(x, 65, z, Material.STONE_BRICK_WALL);
        }
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            block(x, 64, z, x*x + z*z <= 12 ? Material.CYAN_CONCRETE : Material.POLISHED_DEEPSLATE);
        }
        block(0, 64, 0, Material.SEA_LANTERN);
        for (int[] p : new int[][]{{-15,-15},{15,-15},{-15,15},{15,15}}) {
            for (int y=65; y<=73; y++) for (int dx=-1; dx<=1; dx++) for (int dz=-1; dz<=1; dz++)
                block(p[0]+dx,y,p[1]+dz,y==73 ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS);
            block(p[0],74,p[1],Material.CAMPFIRE);
            for (int d=-4; d<=4; d++) block(p[0]+d,72,p[1],Material.STONE_BRICK_SLAB);
        }
        for (int x=-13; x<=13; x++) {
            for (int y=65; y<=68; y++) block(x,y,-19,Material.STONE_BRICKS);
            block(x,69,-19,Material.STONE_BRICK_SLAB);
        }
        for (int x : new int[]{-9,-3,3,9}) {
            for (int y=69; y<=74; y++) block(x,y,-19,Material.STONE_BRICKS);
            block(x,75,-19,Material.CHISELED_STONE_BRICKS);
        }
        for (int x=-9; x<=9; x++) block(x,74,-19,Material.STONE_BRICK_SLAB);
        for (int z=-17; z<=17; z+=6) for (int x : new int[]{-18,18}) {
            block(x,65,z,Material.CHISELED_STONE_BRICKS);
            block(x,66,z,Material.LANTERN);
        }
        for (int[] p:new int[][]{{-17,-8},{17,-8},{-17,8},{17,8}}) {
            for(int y=65;y<70;y++) block(p[0],y,p[1],Material.DARK_OAK_LOG);
            for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) for(int dy=0;dy<=2;dy++)
                if(dx*dx+dz*dz+dy*dy<9) block(p[0]+dx,69+dy,p[1]+dz,Material.OAK_LEAVES);
        }
        for (int x=-18;x<=18;x++) for(int z=-20;z<=20;z++) {
            if(Math.abs(x)>12 && (x*x+3*z*z)%11==0) block(x,64,z,Material.MOSSY_STONE_BRICKS);
        }
        for (int[] p : RELAYS) {
            block(p[0],65,p[1],Material.POLISHED_BLACKSTONE_BRICKS);
            block(p[0],66,p[1],Material.COPPER_BULB);
            block(p[0],67,p[1],Material.LIGHTNING_ROD);
            label(p[0]+.5,68,p[1]+.5,"RELAY · right click",NamedTextColor.AQUA);
        }
        block(0,65,15,Material.LODESTONE);
        block(0,65,18,Material.CHISELED_STONE_BRICKS);
        label(.5,67,15.5,"OBSERVATORY\nRight click to begin",NamedTextColor.GOLD);
        label(.5,66,18.5,"Two relays → three sentinels → hold the cyan circle",NamedTextColor.WHITE);
        label(.5,66,-5.5,"RIFT · hold for 15 seconds",NamedTextColor.AQUA);
    }

    private void label(double x,double y,double z,String text,NamedTextColor color) {
        TextDisplay display=world.spawn(new Location(world,x,y,z),TextDisplay.class);
        display.text(Component.text(text,color));
        display.setBillboard(Display.Billboard.CENTER);
        display.setPersistent(true);
        display.getPersistentDataContainer().set(guardKey,PersistentDataType.STRING,"label");
    }

    boolean join(Player player) {
        if (engine.snapshot().status() == EncounterStatus.ACTIVE) {
            player.sendMessage("An encounter is running. Join after it finishes or ask an operator to reset.");
            return false;
        }
        player.teleport(new Location(world,.5,65,17.5,180,0));
        player.sendMessage(Component.text("Right click the lodestone to begin. Use your own equipment or /encounter kit.",NamedTextColor.AQUA));
        return true;
    }

    void kit(Player p) {
        if (!p.getWorld().equals(world)) { p.sendMessage("Join the observatory first."); return; }
        Map<Integer, ItemStack> leftovers=p.getInventory().addItem(new ItemStack(Material.IRON_SWORD),new ItemStack(Material.BREAD,16));
        if (!leftovers.isEmpty()) p.sendMessage("Make room in your inventory for the demo kit.");
        // Equip empty armor slots without replacing a player's existing equipment.
        if(empty(p.getInventory().getHelmet())) p.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        if(empty(p.getInventory().getChestplate())) p.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        if(empty(p.getInventory().getLeggings())) p.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        if(empty(p.getInventory().getBoots())) p.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));
        p.sendMessage("Iron sword, armor for empty slots, and bread supplied. Your existing equipment was kept.");
    }

    private static boolean empty(ItemStack item) { return item==null || item.getType().isAir(); }

    TransitionResult start() {
        if (engine.snapshot().status()!=EncounterStatus.IDLE) return TransitionResult.rejected("reset_before_replay");
        participants.clear();
        world.getPlayers().stream().filter(p->p.getGameMode()==GameMode.SURVIVAL || p.getGameMode()==GameMode.ADVENTURE)
                .forEach(p->participants.add(p.getUniqueId()));
        if(participants.isEmpty()) return TransitionResult.rejected("join_in_survival_or_adventure_first");
        TransitionResult result=engine.start();
        for (int i=0;i<3;i++) spawnGuard(-6+i*6,-9,"sentinel");
        announce("Activate both copper relays and defeat the three sentinels.",NamedTextColor.AQUA);
        hud.setVisible(true);
        return result;
    }

    private Zombie spawnGuard(double x,double z,String role) {
        Zombie mob=world.spawn(new Location(world,x,65,z),Zombie.class);
        mob.customName(Component.text(role.equals("sentinel")?"Rift sentinel":"Rift echo",NamedTextColor.RED));
        mob.setCustomNameVisible(true);
        mob.setPersistent(true);
        mob.setRemoveWhenFarAway(false);
        mob.setCanPickupItems(false);
        mob.setAdult();
        mob.getEquipment().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
        mob.getEquipment().setHelmetDropChance(0);
        mob.getPersistentDataContainer().set(guardKey,PersistentDataType.STRING,role);
        guards.add(mob.getUniqueId());
        return mob;
    }

    @EventHandler(ignoreCancelled=true)
    public void interact(PlayerInteractEvent event) {
        if(event.getHand()!=EquipmentSlot.HAND || event.getAction()!=Action.RIGHT_CLICK_BLOCK) return;
        Block clicked=event.getClickedBlock();
        if(clicked==null || !clicked.getWorld().equals(world)) return;
        Player player=event.getPlayer();
        if(clicked.getX()==0 && clicked.getY()==65 && clicked.getZ()==15) {
            event.setCancelled(true);
            if(engine.snapshot().status()==EncounterStatus.COMPLETED) reset();
            TransitionResult result=start();
            if(!result.accepted()) player.sendMessage("Cannot start: "+result.reason());
            return;
        }
        if(clicked.getY()!=66 || !participants.contains(player.getUniqueId())) return;
        for(int i=0;i<RELAYS.length;i++) if(clicked.getX()==RELAYS[i][0] && clicked.getZ()==RELAYS[i][1]) {
            event.setCancelled(true);
            if(activateRelay(i)) player.playSound(player.getLocation(),Sound.BLOCK_BEACON_ACTIVATE,1,1);
        }
    }

    boolean activateRelay(int index) {
        if(index<0 || index>=RELAYS.length || relays.contains(index)) return false;
        TransitionResult result=engine.addProgress("activate_relays",1);
        if(!result.accepted()) return false;
        relays.add(index);
        block(RELAYS[index][0],66,RELAYS[index][1],Material.SEA_LANTERN);
        handle(result);
        return true;
    }

    @EventHandler
    public void death(EntityDeathEvent event) {
        LivingEntity entity=event.getEntity();
        String role=entity.getPersistentDataContainer().get(guardKey,PersistentDataType.STRING);
        if(role==null || !entity.getWorld().equals(world)) return;
        event.getDrops().clear(); event.setDroppedExp(0);
        // A UUID is consumed once; only kills by an enrolled player count.
        boolean tracked=guards.remove(entity.getUniqueId());
        Player killer=entity.getKiller();
        if(tracked && role.equals("sentinel")) {
            if(killer!=null && participants.contains(killer.getUniqueId())) handle(engine.addProgress("clear_sentinels",1));
            else if(engine.snapshot().status()==EncounterStatus.ACTIVE && "stabilize".equals(engine.snapshot().activePhaseId())) {
                int expectedGeneration=generation;
                plugin.getServer().getScheduler().runTask(plugin,()-> {
                    if(generation==expectedGeneration && engine.snapshot().status()==EncounterStatus.ACTIVE
                            && "stabilize".equals(engine.snapshot().activePhaseId()))
                        spawnGuard(entity.getLocation().getX(),entity.getLocation().getZ(),"sentinel");
                });
            }
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void protect(BlockBreakEvent e) { if(e.getBlock().getWorld().equals(world)) e.setCancelled(true); }
    @EventHandler public void quit(PlayerQuitEvent e) { hud.removePlayer(e.getPlayer()); }
    @EventHandler public void playerDeath(PlayerDeathEvent event) {
        Player player=event.getEntity();
        if(!player.getWorld().equals(world)) return;
        arenaDeaths.add(player.getUniqueId());
        if(participants.contains(player.getUniqueId()) && participants.stream().filter(id->!id.equals(player.getUniqueId()))
                .map(Bukkit::getPlayer).filter(Objects::nonNull).noneMatch(p->p.getWorld().equals(world) && !p.isDead())) {
            announce("The expedition fell. The observatory has reset; return to the lodestone to retry.",NamedTextColor.RED);
            reset();
        }
    }
    @EventHandler public void respawn(PlayerRespawnEvent event) {
        if(arenaDeaths.remove(event.getPlayer().getUniqueId())) event.setRespawnLocation(new Location(world,.5,65,17.5,180,0));
    }

    private boolean inZone(Player p) {
        Location loc=p.getLocation();
        return p.getWorld().equals(world) && loc.getY()>=65 && loc.getY()<68 && loc.getX()*loc.getX()+loc.getZ()*loc.getZ()<=12
                && !p.isDead() && (p.getGameMode()==GameMode.SURVIVAL || p.getGameMode()==GameMode.ADVENTURE);
    }

    private void tick() {
        clock++;
        EncounterSnapshot snapshot=engine.snapshot();
        for(Player p:Bukkit.getOnlinePlayers()) {
            if(p.getWorld().equals(world)) hud.addPlayer(p); else hud.removePlayer(p);
        }
        if(snapshot.status()==EncounterStatus.ACTIVE && "seal_rift".equals(snapshot.activePhaseId())) {
            boolean occupied=participants.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).anyMatch(this::inZone);
            if(occupied) {
                holdClock++;
                if(holdClock>=20) { holdClock=0; handle(engine.addProgress("hold_zone_ticks",1)); }
                if(++waveClock==80 || waveClock==180) spawnGuard(0,-7,"echo");
            } else holdClock=0;
        }
        if(clock%10==0) renderHud();
        if(clock%20==0 && snapshot.status()==EncounterStatus.ACTIVE) {
            world.spawnParticle(Particle.END_ROD,new Location(world,.5,66,.5),12,2,.2,2,.01);
        }
        if(snapshot.status()==EncounterStatus.ACTIVE && participants.stream().map(Bukkit::getPlayer).filter(Objects::nonNull)
                .noneMatch(p->p.getWorld().equals(world))) reset();
    }

    private void handle(TransitionResult result) {
        if(!result.accepted()) return;
        for(EncounterEvent event:result.events()) {
            if(event instanceof EncounterEvent.PhaseCompleted phase && phase.nextPhaseId()!=null)
                announce("Rift unlocked. Hold the cyan circle for 15 seconds; leaving pauses progress.",NamedTextColor.AQUA);
            if(event instanceof EncounterEvent.EncounterCompleted && !rewarded) {
                rewarded=true;
                guards.stream().map(Bukkit::getEntity).filter(Objects::nonNull).forEach(Entity::remove);
                guards.clear();
                for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) block(x,64,z,Material.IRON_BLOCK);
                block(0,65,0,Material.BEACON);
                for(UUID id:participants) {
                    Player p=Bukkit.getPlayer(id);
                    if(p!=null && p.getWorld().equals(world)) {
                        p.getInventory().addItem(new ItemStack(Material.AMETHYST_SHARD,1)).values()
                                .forEach(item->world.dropItemNaturally(p.getLocation(),item));
                        p.showTitle(net.kyori.adventure.title.Title.title(Component.text("RIFT SEALED",NamedTextColor.AQUA),Component.text("Observatory secured · amethyst shard awarded")));
                        p.playSound(p.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,1,1);
                    }
                }
                world.spawnParticle(Particle.END_ROD,new Location(world,.5,68,.5),100,2,3,2,.1);
                announce("Observatory secured. Right click the lodestone to play again.",NamedTextColor.GREEN);
            }
        }
        renderHud();
        plugin.getLogger().info("Encounter transition: "+result.events());
    }

    private void announce(String text,NamedTextColor color) {
        for(Player p:world.getPlayers()) p.sendMessage(Component.text(text,color));
    }

    private void renderHud() {
        EncounterSnapshot s=engine.snapshot();
        int r=s.objectiveProgress().get("activate_relays"),g=s.objectiveProgress().get("clear_sentinels"),h=s.objectiveProgress().get("hold_zone_ticks");
        if(s.status()==EncounterStatus.IDLE) { hud.setTitle("OBSERVATORY · right click the lodestone to begin"); hud.setProgress(0); hud.setColor(BarColor.BLUE); }
        else if(s.status()==EncounterStatus.COMPLETED) { hud.setTitle("RIFT SEALED · observatory secured · click lodestone to replay"); hud.setProgress(1); hud.setColor(BarColor.GREEN); }
        else if("stabilize".equals(s.activePhaseId())) { hud.setTitle("STABILIZE · relays "+r+"/2 · sentinels "+g+"/3"); hud.setProgress((r+g)/5.0); hud.setColor(BarColor.BLUE); }
        else { hud.setTitle("SEAL RIFT · hold the cyan circle · "+h+"/15 seconds"); hud.setProgress(h/15.0); hud.setColor(BarColor.PURPLE); }
        String hint=s.status()==EncounterStatus.IDLE ? "Right click the lodestone to begin" :
                s.status()==EncounterStatus.COMPLETED ? "Right click the lodestone to play again" :
                "stabilize".equals(s.activePhaseId()) ? "Right click copper relays · defeat the sentinels" :
                "Stay in the cyan circle · leaving pauses progress";
        for(Player p:world.getPlayers()) p.sendActionBar(Component.text(hint,NamedTextColor.AQUA));
    }

    TransitionResult reset() {
        generation++;
        guards.stream().map(Bukkit::getEntity).filter(Objects::nonNull).forEach(Entity::remove);
        guards.clear(); relays.clear(); participants.clear(); rewarded=false; holdClock=0; waveClock=0;
        for(int[] p:RELAYS) block(p[0],66,p[1],Material.COPPER_BULB);
        block(0,65,0,Material.AIR);
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) block(x,64,z,Material.CYAN_CONCRETE);
        block(0,64,0,Material.SEA_LANTERN);
        TransitionResult result=engine.reset(); renderHud(); return result;
    }

    private void cleanup() {
        for(Entity entity:world.getEntities()) if(entity.getPersistentDataContainer().has(guardKey,PersistentDataType.STRING)) entity.remove();
    }
    void close() { if(world!=null) { reset(); cleanup(); } hud.removeAll(); }
}
