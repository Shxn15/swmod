package com.shxn15.swmod.lightsaber;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.shxn15.swmod.combat.SaberCombatSystem;
import com.shxn15.swmod.combat.SaberDefense;
import com.shxn15.swmod.combat.SaberTuning;
import com.shxn15.swmod.registry.ModDamageTypes;
import com.shxn15.swmod.registry.ModDataComponents;
import com.shxn15.swmod.registry.ModEntityTypes;
import com.shxn15.swmod.registry.ModSounds;

/**
 * Ein geworfenes, rotierendes Lichtschwert. Fliegt geradeaus, trifft alles auf seinem Weg
 * und kehrt dann wie ein Bumerang zum Werfer zurück. Das Item bleibt die ganze Zeit im Inventar
 * (als "geworfen" markiert), so kann es weder verloren gehen noch verdoppelt werden.
 */
public class ThrownLightsaberEntity extends Projectile {
	private static final EntityDataAccessor<ItemStack> DATA_STACK = SynchedEntityData.defineId(ThrownLightsaberEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<Boolean> DATA_RETURNING = SynchedEntityData.defineId(ThrownLightsaberEntity.class, EntityDataSerializers.BOOLEAN);

	private int throwId;
	private int age;
	private final IntSet hitOutbound = new IntOpenHashSet();
	private final IntSet hitReturning = new IntOpenHashSet();

	public ThrownLightsaberEntity(EntityType<? extends ThrownLightsaberEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public ThrownLightsaberEntity(Level level, LivingEntity owner, ItemStack stack, int throwId) {
		this(ModEntityTypes.THROWN_LIGHTSABER, level);
		this.setOwner(owner);
		this.throwId = throwId;
		this.entityData.set(DATA_STACK, stack);

		Vec3 look = owner.getLookAngle();
		Vec3 start = owner.getEyePosition().add(look.scale(0.7)).add(0, -0.25, 0);
		this.setPos(start.x, start.y, start.z);
		this.setDeltaMovement(look.scale(SaberTuning.THROW_SPEED));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_STACK, ItemStack.EMPTY);
		builder.define(DATA_RETURNING, false);
	}

	public ItemStack getStack() {
		return this.entityData.get(DATA_STACK);
	}

	public boolean isReturning() {
		return this.entityData.get(DATA_RETURNING);
	}

	public int getAge() {
		return this.age;
	}

	@Override
	public void tick() {
		super.tick();
		this.age++;

		if (this.level() instanceof ServerLevel serverLevel) {
			this.serverTick(serverLevel);
		} else {
			Vec3 motion = this.getDeltaMovement();
			this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
		}
	}

	private void serverTick(ServerLevel level) {
		Entity owner = this.getOwner();
		if (!(owner instanceof LivingEntity livingOwner) || !owner.isAlive() || owner.level() != level || this.age > SaberTuning.THROW_MAX_TICKS) {
			this.discard();
			return;
		}

		if (!this.isReturning() && this.age >= SaberTuning.THROW_OUTBOUND_TICKS) {
			this.startReturning();
		}

		Vec3 from = this.position();
		Vec3 motion;

		if (this.isReturning()) {
			Vec3 target = livingOwner.getEyePosition().add(0, -0.45, 0);
			Vec3 toTarget = target.subtract(from);
			double distance = toTarget.length();
			if (distance < 1.3) {
				this.catchBy(livingOwner);
				return;
			}
			Vec3 desired = toTarget.normalize().scale(Math.min(SaberTuning.THROW_RETURN_SPEED, distance));
			motion = this.getDeltaMovement().lerp(desired, 0.35);
		} else {
			motion = this.getDeltaMovement();
			BlockHitResult blockHit = level.clip(new ClipContext(from, from.add(motion), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
			if (blockHit.getType() != HitResult.Type.MISS) {
				Vec3 hit = blockHit.getLocation();
				motion = hit.subtract(from).scale(0.9);
				level.playSound(null, hit.x, hit.y, hit.z, ModSounds.LIGHTSABER_BLOCK, SoundSource.PLAYERS, 0.8f, 1.3f);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hit.x, hit.y, hit.z, 12, 0.05, 0.05, 0.05, 0.4);
				level.sendParticles(ParticleTypes.SMOKE, hit.x, hit.y, hit.z, 4, 0.05, 0.05, 0.05, 0.01);
				this.startReturning();
			}
		}

		Vec3 to = from.add(motion);
		if (this.hitEntities(level, livingOwner, from, to)) {
			// Abgewehrt: sofort umkehren.
			motion = motion.scale(-0.5);
			to = from.add(motion);
			this.startReturning();
		}

		this.setDeltaMovement(motion);
		this.setPos(to.x, to.y, to.z);
	}

	/** @return true, wenn das Schwert von einem blockenden Spieler abgewehrt wurde */
	private boolean hitEntities(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 to) {
		IntSet alreadyHit = this.isReturning() ? this.hitReturning : this.hitOutbound;
		AABB sweep = new AABB(from, to).inflate(SaberTuning.THROW_HIT_RADIUS);

		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, sweep,
				e -> e != owner && e.isAlive() && !e.isSpectator())) {
			if (alreadyHit.contains(target.getId())) continue;

			AABB box = target.getBoundingBox().inflate(SaberTuning.THROW_HIT_RADIUS * 0.7);
			if (!box.contains(from) && box.clip(from, to).isEmpty()) continue;

			alreadyHit.add(target.getId());

			if (target instanceof ServerPlayer defender && SaberDefense.tryBlockThrown(defender, this)) {
				return true;
			}

			DamageSource source = ModDamageTypes.thrownLightsaber(level, this, owner);
			target.invulnerableTime = 0;
			if (target.hurtServer(level, source, SaberTuning.THROW_DAMAGE)) {
				level.playSound(null, target.getX(), target.getY(0.5), target.getZ(), ModSounds.LIGHTSABER_HIT, SoundSource.PLAYERS, 1.0f, 1.0f);
				level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY(0.5), target.getZ(), 6, 0.1, 0.1, 0.1, 0.01);
			}
		}
		return false;
	}

	private void startReturning() {
		this.entityData.set(DATA_RETURNING, true);
	}

	private void catchBy(LivingEntity owner) {
		if (owner instanceof Player player) {
			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				ItemStack stack = player.getInventory().getItem(slot);
				if (Lightsabers.isLightsaber(stack) && Lightsabers.data(stack).throwId() == this.throwId) {
					stack.set(ModDataComponents.LIGHTSABER, Lightsabers.data(stack).caught());
					break;
				}
			}
			SaberCombatSystem.onCaught(player);
		}

		this.level().playSound(null, owner.getX(), owner.getEyeY(), owner.getZ(), ModSounds.LIGHTSABER_CATCH, SoundSource.PLAYERS, 1.0f, 1.0f);
		this.discard();
	}

	/** Geworfene Lichtschwerter können nicht beschädigt werden. */
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 128 * 128;
	}
}
