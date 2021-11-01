package dozono.hearthstone;

import dozono.hearthstone.client.HearthStoneClientSetup;
import dozono.hearthstone.client.gui.HeartStoneGui;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.IWorldInfo;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Random;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(HearthStoneMod.MODID)
public class HearthStoneMod {
    // Directly reference a log4j logger.
    private static final Logger LOGGER = LogManager.getLogger();
    public static final String MODID = "hearthstone";

    @CapabilityInject(HearthStoneStat.class)
    public static Capability<HearthStoneStat> CAPABILITY_HEARTH;

    private static HearthStoneMod instance;

    public static HearthStoneMod getInstance() {
        return instance;
    }

    public static final DataParameter<Integer> DATA_PLAYER_HEARTH_STONE_CHARGE = EntityDataManager.defineId(PlayerEntity.class, DataSerializers.INT);

    private final DeferredRegister<Item> itemRegister = DeferredRegister.create(Item.class, MODID);

    public static final DeferredRegister<SoundEvent> SOUND_REGISTER = DeferredRegister.create(SoundEvent.class, MODID);

    public static final RegistryObject<SoundEvent> CHANELING_SOUND = SOUND_REGISTER.register("chaneling", () -> new SoundEvent(new ResourceLocation(MODID, "chaneling")));
    public static final RegistryObject<SoundEvent> TELEPORT_SOUND = SOUND_REGISTER.register("teleport", () -> new SoundEvent(new ResourceLocation(MODID, "teleport")));

    public final RegistryObject<Item> ITEM_HEARTH_STONE = itemRegister.register("hearth_stone",
            () -> new Item(new Item.Properties().tab(ItemGroup.TAB_MISC)) {
                @Override
                public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context) {
                    if (context.getPlayer() != null && context.isSecondaryUseActive() && context.getClickedPos() != null && !context.getLevel().isClientSide()) {
                        context.getPlayer().sendMessage(new StringTextComponent("Set spawn point at " + context.getClickedPos()), Util.NIL_UUID);
                        stack.getCapability(CAPABILITY_HEARTH).ifPresent(c -> c.target = context.getClickLocation().add(Vector3d.atCenterOf(context.getClickedFace().getNormal())));
                        return ActionResultType.SUCCESS;
                    }
                    return super.onItemUseFirst(stack, context);
                }

                @Override
                public void onUsingTick(ItemStack stack, LivingEntity player, int count) {
//                    player.level.playSound(null, player, HearthStoneMod.CHANELING_SOUND.get(), SoundCategory.AMBIENT, 1, 1);
                    player.playSound(HearthStoneMod.CHANELING_SOUND.get(), 0.6F, 1);
                    if (player.level.isClientSide) {
                        return;
                    }
                    Integer last = player.getEntityData().get(DATA_PLAYER_HEARTH_STONE_CHARGE);
                    player.getEntityData().set(DATA_PLAYER_HEARTH_STONE_CHARGE, last + 1);
                }

                @Override
                public void releaseUsing(ItemStack itemStack, World level, LivingEntity livingEntity, int remaining) {
                    if (level.isClientSide()) return;
                    itemStack.getCapability(CAPABILITY_HEARTH).ifPresent((h) -> {
                        if (livingEntity.getEntityData().get(DATA_PLAYER_HEARTH_STONE_CHARGE) > 80) {
                            Vector3d blockPos = Optional.ofNullable(h.target.length() == 0 ? null : h.target)
                                    .orElseGet(() -> {
                                        IWorldInfo levelData = level.getLevelData();
//                                        BlockPos pos = new BlockPos(levelData.getXSpawn(), levelData.getYSpawn(), levelData.getZSpawn());
                                        if (livingEntity instanceof ServerPlayerEntity) {
                                            ServerPlayerEntity player = (ServerPlayerEntity) livingEntity;
                                            int x = player.level.random.nextInt(1000) - 500;
                                            x = x >= 0 ? x + 100 : x - 100;
                                            int z = player.level.random.nextInt(1000) - 500;
                                            z = z >= 0 ? z + 100 : z - 100;
                                            player.level.getBlockState(new BlockPos(x, 0, z));
                                            int y = player.level.getHeight(Heightmap.Type.WORLD_SURFACE, x, z);
                                            return new Vector3d(x + 0.5, y + 1, z + 0.5);
                                        }
                                        return Vector3d.ZERO;
                                    });
                            livingEntity.level.playSound(null, livingEntity, HearthStoneMod.TELEPORT_SOUND.get(), SoundCategory.PLAYERS, 1, 1);
                            livingEntity.teleportTo(blockPos.x(), blockPos.y(), blockPos.z());
                        }
                    });
                    livingEntity.getEntityData().set(DATA_PLAYER_HEARTH_STONE_CHARGE, 0);
                }

                @Override
                public ActionResult<ItemStack> use(World world, PlayerEntity playerEntity, Hand hand) {
                    ItemStack itemstack = playerEntity.getItemInHand(hand);
                    playerEntity.startUsingItem(hand);
                    return ActionResult.consume(itemstack);
                }

                @Override
                public int getUseDuration(ItemStack p_77626_1_) {
                    return 72000;
                }

                @Override
                public UseAction getUseAnimation(ItemStack p_77661_1_) {
                    return UseAction.BOW;
                }
            });

    public HearthStoneMod() {
        instance = this;
        // Register the setup method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);

        itemRegister.register(FMLJavaModLoadingContext.get().getModEventBus());
        SOUND_REGISTER.register(FMLJavaModLoadingContext.get().getModEventBus());
        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> HearthStoneClientSetup::setup);
    }

    private void setup(final FMLCommonSetupEvent event) {
        // some preinit code
        CapabilityManager.INSTANCE.register(HearthStoneStat.class, new Capability.IStorage<HearthStoneStat>() {
            @Nullable
            @Override
            public INBT writeNBT(Capability<HearthStoneStat> capability, HearthStoneStat instance, Direction side) {
                CompoundNBT compoundnbt = new CompoundNBT();
                compoundnbt.putDouble("X", instance.target.x());
                compoundnbt.putDouble("Y", instance.target.y());
                compoundnbt.putDouble("Z", instance.target.z());
                return compoundnbt;
            }

            @Override
            public void readNBT(Capability<HearthStoneStat> capability, HearthStoneStat instance, Direction side, INBT nbt) {
                CompoundNBT compoundNBT = (CompoundNBT) nbt;
                instance.target = new Vector3d(compoundNBT.getDouble("X"), compoundNBT.getDouble("Y"), compoundNBT.getDouble("Z"));
            }
        }, HearthStoneStat::new);
    }

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class CommonEvents {
        @SubscribeEvent
        public static void onAttachCapability(final AttachCapabilitiesEvent<ItemStack> event) {
            if (event.getObject().getItem() == HearthStoneMod.getInstance().ITEM_HEARTH_STONE.get())
                event.addCapability(new ResourceLocation("hearthstone", MODID), new ICapabilitySerializable() {
                    @Override
                    public INBT serializeNBT() {
                        return CAPABILITY_HEARTH.writeNBT(stoneStat, Direction.DOWN);
                    }

                    @Override
                    public void deserializeNBT(INBT nbt) {
                        CAPABILITY_HEARTH.readNBT(stoneStat, Direction.DOWN, nbt);
                    }

                    final HearthStoneStat stoneStat = new HearthStoneStat();

                    @Nonnull
                    @Override
                    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
                        if (cap == CAPABILITY_HEARTH) {
                            return (LazyOptional<T>) LazyOptional.of(() -> stoneStat);
                        }
                        return LazyOptional.empty();
                    }
                });
        }

        @SubscribeEvent
        public static void onAttrCreation(EntityEvent.EntityConstructing event) {
            if (event.getEntity() instanceof PlayerEntity) {
                PlayerEntity entity = (PlayerEntity) event.getEntity();
                entity.getEntityData().define(DATA_PLAYER_HEARTH_STONE_CHARGE, 0);
            }
        }

        @SubscribeEvent
        public static void onPlayerHurtEvent(LivingHurtEvent event) {
            LivingEntity entity = event.getEntityLiving();
            if (entity instanceof PlayerEntity && !entity.level.isClientSide) {
                entity.getEntityData().set(DATA_PLAYER_HEARTH_STONE_CHARGE, 0);
            }
        }
    }
}
