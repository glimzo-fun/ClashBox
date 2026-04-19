package net.glimzo.clashbox.core;

import net.glimzo.clashbox.arena.ArenaBlockManager;
import net.glimzo.clashbox.arena.OreRegenerationManager;
import net.glimzo.clashbox.arena.OreCubeManager;
import net.glimzo.clashbox.arena.PitEntryManager;
import net.glimzo.clashbox.arena.SafeLandingHandler;
import net.glimzo.clashbox.combat.AssistTracker;
import net.glimzo.clashbox.combat.BountyManager;
import net.glimzo.clashbox.combat.DeathHandler;
import net.glimzo.clashbox.combat.KillManager;
import net.glimzo.clashbox.combat.StreakTracker;
import net.glimzo.clashbox.commands.ArenaAdminCommand;
import net.glimzo.clashbox.commands.DepositCommand;
import net.glimzo.clashbox.commands.BountyCommand;
import net.glimzo.clashbox.commands.ClashBoxCommand;
import net.glimzo.clashbox.commands.EnchantNpcCommand;
import net.glimzo.clashbox.commands.LeaderboardCommand;
import net.glimzo.clashbox.commands.OreAdminCommand;
import net.glimzo.clashbox.commands.OreCubeAdminCommand;
import net.glimzo.clashbox.commands.SetAdminCommand;
import net.glimzo.clashbox.commands.StatsCommand;
import net.glimzo.clashbox.commands.TeamCommand;
import net.glimzo.clashbox.data.ClashBoxDataManager;
import net.glimzo.clashbox.economy.BankManager;
import net.glimzo.clashbox.economy.EconomyHook;
import net.glimzo.clashbox.economy.WalletService;
import net.glimzo.clashbox.events.EventManager;
import net.glimzo.clashbox.player.ClashBoxProfileManager;
import net.glimzo.clashbox.player.PlayerStateManager;
import net.glimzo.clashbox.portal.PortalManager;
import net.glimzo.clashbox.progression.UpgradeManager;
import net.glimzo.clashbox.sets.SetBonusListener;
import net.glimzo.clashbox.sets.SetManager;
import net.glimzo.clashbox.sets.SetMaterialDropListener;
import net.glimzo.clashbox.sets.SetRoomInteractListener;
import net.glimzo.clashbox.team.TeamManager;
import net.glimzo.clashbox.ui.ActionBarService;
import net.glimzo.clashbox.ui.ScoreboardService;
import net.glimzo.clashbox.ui.TitleService;
import net.glimzo.clashbox.zone.ZoneConfig;
import net.glimzo.clashbox.zone.ZoneManager;
import net.glimzo.clashbox.bank.BankCommandHandler;
import net.glimzo.clashbox.bank.ChequeManager;
import net.glimzo.clashbox.bank.BankConfig;
import net.glimzo.clashbox.bank.CreditManager;
import net.glimzo.clashbox.bank.LoanManager;
import net.glimzo.clashbox.bank.SavingsManager;
import net.glimzo.clashbox.bank.ShardEconomy;
import net.glimzo.clashbox.bank.TransactionLogger;
import net.glimzo.clashbox.enchant.EnchantApplyMenu;
import net.glimzo.clashbox.enchant.EnchantEffectManager;
import net.glimzo.clashbox.enchant.EnchantManager;
import net.glimzo.clashbox.sell.SellArea;
import net.glimzo.clashbox.sell.SellManager;
import net.glimzo.clashbox.tier.TierManager;
import net.glimzo.clashbox.utilities.ConfigFile;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClashBoxPlugin extends JavaPlugin {

    public static final String LOBBY_WORLD = "world";
    public static final String ARENA_WORLD = "box";

    private static ClashBoxPlugin instance;

    private ClashBoxConfig      config;
    private ZoneConfig          zoneConfig;

    private ClashBoxDataManager     dataManager;
    private ClashBoxProfileManager  profileManager;
    private PlayerStateManager      playerStateManager;
    private ZoneManager             zoneManager;
    private WalletService           walletService;
    private BankManager             bankManager;
    private EconomyHook             economyHook;
    private StreakTracker           streakTracker;
    private AssistTracker           assistTracker;
    private BountyManager           bountyManager;
    private KillManager             killManager;
    private DeathHandler            deathHandler;
    private OreRegenerationManager  oreRegenerationManager;
    private OreCubeManager          oreCubeManager;
    private ArenaBlockManager       arenaBlockManager;
    private PitEntryManager         pitEntryManager;
    private SafeLandingHandler      safeLandingHandler;
    private PortalManager           portalManager;
    private EventManager            eventManager;
    private TeamManager             teamManager;
    private UpgradeManager          upgradeManager;
    private OreAdminCommand         oreAdminCommand;
    private OreCubeAdminCommand     oreCubeAdminCommand;
    private ArenaAdminCommand       arenaAdminCommand;
    private EnchantNpcCommand       enchantNpcCommand;
    private SetAdminCommand         setAdminCommand;

    private BankConfig          bankConfig;
    private ShardEconomy        shardEconomy;
    private TransactionLogger   transactionLogger;
    private SavingsManager      savingsManager;
    private CreditManager       creditManager;
    private LoanManager         loanManager;
    private BankCommandHandler  bankCommandHandler;
    private ChequeManager       chequeManager;

    private TierManager             tierManager;
    private SellManager             sellManager;
    private SellArea                sellArea;
    private EnchantManager          enchantManager;
    private EnchantEffectManager    enchantEffectManager;
    private FileConfiguration       enchantConfig;
    private ScoreboardService       scoreboardService;
    private ActionBarService        actionBarService;
    private TitleService            titleService;

    private SetManager              setManager;
    private SetMaterialDropListener setMaterialDropListener;
    private SetBonusListener        setBonusListener;
    private SetRoomInteractListener setRoomInteractListener;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        this.config     = new ClashBoxConfig(this);
        this.zoneConfig = new ZoneConfig(this);

        this.dataManager        = new ClashBoxDataManager(this);
        this.dataManager.init();

        this.playerStateManager = new PlayerStateManager(this);
        this.profileManager     = new ClashBoxProfileManager(this, dataManager);
        this.walletService      = new WalletService(this);
        this.bankManager        = new BankManager(this);
        this.economyHook        = new EconomyHook(this, walletService, bankManager);
        this.streakTracker      = new StreakTracker(this);
        this.assistTracker      = new AssistTracker(this);
        this.bountyManager      = new BountyManager(this);
        this.killManager        = new KillManager(this, streakTracker, bountyManager, assistTracker);
        this.deathHandler       = new DeathHandler(this, killManager, playerStateManager);
        this.zoneManager        = new ZoneManager(this, playerStateManager);
        this.oreRegenerationManager = new OreRegenerationManager(this);
        this.oreCubeManager     = new OreCubeManager(this);
        this.arenaBlockManager  = new ArenaBlockManager(this);
        this.pitEntryManager    = new PitEntryManager(this, playerStateManager);
        this.safeLandingHandler = new SafeLandingHandler(this, playerStateManager);
        this.portalManager      = new PortalManager(this, playerStateManager);
        this.eventManager       = new EventManager(this, economyHook);
        this.teamManager        = new TeamManager(this, economyHook);
        this.upgradeManager     = new UpgradeManager(this, economyHook);

        this.bankConfig         = new BankConfig(this);
        this.shardEconomy       = new ShardEconomy(this, bankConfig);
        this.transactionLogger  = new TransactionLogger(this, bankConfig);
        this.savingsManager     = new SavingsManager(this, bankConfig, shardEconomy, transactionLogger);
        this.creditManager      = new CreditManager(this, bankConfig);
        this.loanManager        = new LoanManager(this, bankConfig, shardEconomy, creditManager, transactionLogger);
        this.bankCommandHandler = new BankCommandHandler(this, bankConfig, shardEconomy,
                savingsManager, loanManager, creditManager, transactionLogger);
        this.chequeManager      = new ChequeManager(this, shardEconomy, savingsManager);

        this.tierManager          = new TierManager(this);
        this.sellManager          = new SellManager(this);
        this.sellArea             = new SellArea(this);
        this.enchantConfig        = ConfigFile.load(this, "enchants.yml");
        this.enchantEffectManager = new EnchantEffectManager(this);
        this.enchantManager       = new EnchantManager(this);
        this.oreAdminCommand      = new OreAdminCommand(this);
        this.oreCubeAdminCommand  = new OreCubeAdminCommand(this);
        this.arenaAdminCommand    = new ArenaAdminCommand(this);
        this.enchantNpcCommand    = new EnchantNpcCommand(this);
        this.scoreboardService    = new ScoreboardService(this);
        this.actionBarService     = new ActionBarService(this);
        this.titleService         = new TitleService(this);

        this.setManager              = new SetManager(this);
        this.setManager.load();
        this.setMaterialDropListener = new SetMaterialDropListener(this);
        this.setBonusListener        = new SetBonusListener(this);
        this.setRoomInteractListener = new SetRoomInteractListener(this);
        this.setAdminCommand         = new SetAdminCommand(this);

        registerServices();
        registerListeners();
        registerCommands();
        startTasks();

        getLogger().info("ClashBox v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (setManager    != null) setManager.stopBonusTask();
        if (oreCubeManager != null) oreCubeManager.stopTask();
        if (profileManager != null) profileManager.saveAll();
        if (portalManager  != null) portalManager.cleanup();
        if (eventManager   != null) eventManager.shutdown();
        ServiceRegistry.clear();
        instance = null;
        getLogger().info("[ClashBox] Disabled - all data saved.");
    }

    private void registerServices() {
        ServiceRegistry.register(ClashBoxConfig.class,          config);
        ServiceRegistry.register(ZoneConfig.class,              zoneConfig);
        ServiceRegistry.register(ClashBoxDataManager.class,     dataManager);
        ServiceRegistry.register(ClashBoxProfileManager.class,  profileManager);
        ServiceRegistry.register(PlayerStateManager.class,      playerStateManager);
        ServiceRegistry.register(ZoneManager.class,             zoneManager);
        ServiceRegistry.register(WalletService.class,           walletService);
        ServiceRegistry.register(BankManager.class,             bankManager);
        ServiceRegistry.register(EconomyHook.class,             economyHook);
        ServiceRegistry.register(StreakTracker.class,           streakTracker);
        ServiceRegistry.register(AssistTracker.class,           assistTracker);
        ServiceRegistry.register(BountyManager.class,           bountyManager);
        ServiceRegistry.register(KillManager.class,             killManager);
        ServiceRegistry.register(DeathHandler.class,            deathHandler);
        ServiceRegistry.register(OreRegenerationManager.class,  oreRegenerationManager);
        ServiceRegistry.register(OreCubeManager.class,          oreCubeManager);
        ServiceRegistry.register(ArenaBlockManager.class,       arenaBlockManager);
        ServiceRegistry.register(PitEntryManager.class,         pitEntryManager);
        ServiceRegistry.register(SafeLandingHandler.class,      safeLandingHandler);
        ServiceRegistry.register(PortalManager.class,           portalManager);
        ServiceRegistry.register(EventManager.class,            eventManager);
        ServiceRegistry.register(TeamManager.class,             teamManager);
        ServiceRegistry.register(UpgradeManager.class,          upgradeManager);
        ServiceRegistry.register(SetManager.class,              setManager);
        ServiceRegistry.register(ScoreboardService.class,       scoreboardService);
        ServiceRegistry.register(ActionBarService.class,        actionBarService);
        ServiceRegistry.register(TitleService.class,            titleService);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(profileManager,          this);
        getServer().getPluginManager().registerEvents(playerStateManager,      this);
        getServer().getPluginManager().registerEvents(zoneManager,             this);
        getServer().getPluginManager().registerEvents(pitEntryManager,         this);
        getServer().getPluginManager().registerEvents(safeLandingHandler,      this);
        getServer().getPluginManager().registerEvents(deathHandler,            this);
        getServer().getPluginManager().registerEvents(killManager,             this);
        getServer().getPluginManager().registerEvents(assistTracker,           this);
        getServer().getPluginManager().registerEvents(oreRegenerationManager,  this);
        getServer().getPluginManager().registerEvents(oreCubeManager,          this);
        getServer().getPluginManager().registerEvents(arenaBlockManager,       this);
        getServer().getPluginManager().registerEvents(portalManager,           this);
        getServer().getPluginManager().registerEvents(teamManager,             this);
        getServer().getPluginManager().registerEvents(upgradeManager,          this);
        getServer().getPluginManager().registerEvents(bankCommandHandler,      this);
        getServer().getPluginManager().registerEvents(sellManager,             this);
        getServer().getPluginManager().registerEvents(sellArea,                this);
        getServer().getPluginManager().registerEvents(enchantEffectManager,    this);
        getServer().getPluginManager().registerEvents(enchantManager,          this);
        getServer().getPluginManager().registerEvents(
                new EnchantApplyMenu.TableInteractListener(this), this);
        getServer().getPluginManager().registerEvents(scoreboardService,       this);
        getServer().getPluginManager().registerEvents(setMaterialDropListener, this);
        getServer().getPluginManager().registerEvents(setBonusListener,        this);
        getServer().getPluginManager().registerEvents(setRoomInteractListener, this);
    }

    private void registerCommands() {
        ClashBoxCommand cbCmd = new ClashBoxCommand(this);
        getCommand("clashbox").setExecutor(cbCmd);
        getCommand("clashbox").setTabCompleter(cbCmd);

        getCommand("bank").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof org.bukkit.entity.Player p)) {
                sender.sendMessage("Players only."); return true;
            }
            bankCommandHandler.handleCommand(p, args);
            return true;
        });
        getCommand("stats").setExecutor(new StatsCommand(this));
        getCommand("leaderboard").setExecutor(new LeaderboardCommand(this));
        getCommand("bounty").setExecutor(new BountyCommand(this));
        getCommand("team").setExecutor(new TeamCommand(this));

        DepositCommand depositCmd = new DepositCommand(this);
        getCommand("deposit").setExecutor(depositCmd);
        getCommand("deposit").setTabCompleter(depositCmd);
    }

    private void startTasks() {
        scoreboardService.startTask();
        actionBarService.startTask();
        oreRegenerationManager.startTask();
        portalManager.startTask();
        eventManager.startScheduler();
        savingsManager.startMaturityChecker();
        enchantEffectManager.startVeinSenseTask();
        loanManager.startCompoundTask();
        transactionLogger.init();
        setManager.startBonusTask();
        setMaterialDropListener.startRegenTask();

        getServer().getScheduler().runTaskLater(this, () -> {
            int cubes = oreCubeManager.loadCubes();
            getLogger().info("[ClashBox] Loaded " + cubes + " ore cube(s).");
            oreCubeManager.startTask();

            int nodes = oreAdminCommand.loadNodes();
            if (nodes > 0) getLogger().info("[ClashBox] Loaded " + nodes + " ore nodes.");

            int glassNodes = setMaterialDropListener.loadNodes();
            if (glassNodes > 0) getLogger().info("[ClashBox] Loaded " + glassNodes + " glass node(s).");

            arenaBlockManager.loadAndSnapshot();
        }, 2L);
    }

    public static ClashBoxPlugin getInstance()          { return instance; }
    public ClashBoxConfig getCBConfig()                 { return config; }
    public ZoneConfig getZoneConfig()                   { return zoneConfig; }
    public ClashBoxDataManager getDataManager()         { return dataManager; }
    public ClashBoxProfileManager getProfileManager()   { return profileManager; }
    public PlayerStateManager getPlayerStateManager()   { return playerStateManager; }
    public ZoneManager getZoneManager()                 { return zoneManager; }
    public EconomyHook getEconomyHook()                 { return economyHook; }
    public WalletService getWalletService()             { return walletService; }
    public BankManager getBankManager()                 { return bankManager; }
    public StreakTracker getStreakTracker()              { return streakTracker; }
    public BountyManager getBountyManager()             { return bountyManager; }
    public KillManager getKillManager()                 { return killManager; }
    public DeathHandler getDeathHandler()               { return deathHandler; }
    public OreRegenerationManager getOreRegenManager()  { return oreRegenerationManager; }
    public OreCubeManager getOreCubeManager()           { return oreCubeManager; }
    public ArenaBlockManager getArenaBlockManager()     { return arenaBlockManager; }
    public PitEntryManager getPitEntryManager()         { return pitEntryManager; }
    public PortalManager getPortalManager()             { return portalManager; }
    public EventManager getEventManager()               { return eventManager; }
    public TeamManager getTeamManager()                 { return teamManager; }
    public UpgradeManager getUpgradeManager()           { return upgradeManager; }
    public OreAdminCommand getOreAdminCommand()         { return oreAdminCommand; }
    public OreCubeAdminCommand getOreCubeAdminCommand() { return oreCubeAdminCommand; }
    public ArenaAdminCommand getArenaAdminCommand()     { return arenaAdminCommand; }
    public EnchantNpcCommand getEnchantNpcCommand()     { return enchantNpcCommand; }
    public SetAdminCommand getSetAdminCommand()         { return setAdminCommand; }
    public BankConfig getBankConfig()                   { return bankConfig; }
    public ShardEconomy getShardEconomy()               { return shardEconomy; }
    public TransactionLogger getTransactionLogger()     { return transactionLogger; }
    public SavingsManager getSavingsManager()           { return savingsManager; }
    public CreditManager getCreditManager()             { return creditManager; }
    public LoanManager getLoanManager()                 { return loanManager; }
    public BankCommandHandler getBankCommandHandler()   { return bankCommandHandler; }
    public ChequeManager getChequeManager()             { return chequeManager; }
    public TierManager getTierManager()                 { return tierManager; }
    public SellManager getSellManager()                 { return sellManager; }
    public SellArea getSellArea()                       { return sellArea; }
    public EnchantManager getEnchantManager()           { return enchantManager; }
    public EnchantEffectManager getEnchantEffectManager() { return enchantEffectManager; }
    public FileConfiguration getEnchantConfig()         { return enchantConfig; }
    public ScoreboardService getScoreboardService()     { return scoreboardService; }
    public ActionBarService getActionBarService()       { return actionBarService; }
    public TitleService getTitleService()               { return titleService; }
    public SetManager getSetManager()                   { return setManager; }
    public SetMaterialDropListener getSetMaterialDropListener() { return setMaterialDropListener; }
}
