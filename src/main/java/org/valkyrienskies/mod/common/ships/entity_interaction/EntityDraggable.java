package org.valkyrienskies.mod.common.ships.entity_interaction;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.entity.EntityShipMovementData;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

import java.util.List;

/**
 * This class handles the logic of moving entities with the ships they're standing on.
 */
public class EntityDraggable {

    /**
     * Moves entities such that they move with the ship below them.
     */
    public static void tickAddedVelocityForWorld(World world) {
        try {
            for (int i = 0; i < world.loadedEntityList.size(); i++) {
                Entity e = world.loadedEntityList.get(i);
                if (!e.isDead) {
                    addEntityVelocityFromShipBelow(e);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds the ship below velocity to entity.
     */
    private static void addEntityVelocityFromShipBelow(final Entity entity) {
        final IDraggable draggable = EntityDraggable.getDraggableFromEntity(entity);
        final EntityShipMountData mountData = ValkyrienUtils.getMountedShipAndPos(entity);
        final EntityShipMovementData oldEntityShipMovementData = draggable.getEntityShipMovementData();

        final ShipData lastShipTouchedPlayer = oldEntityShipMovementData.getLastTouchedShip();
        final int oldTicksSinceTouchedShip = oldEntityShipMovementData.getTicksSinceTouchedShip();
        final Vector3dc oldVelocityAdded = oldEntityShipMovementData.getAddedLinearVelocity();
        final double oldYawVelocityAdded = oldEntityShipMovementData.getAddedYawVelocity();

        if (lastShipTouchedPlayer == null || oldTicksSinceTouchedShip >= VSConfig.ticksToStickToShip) {
            if (entity.onGround) {
                // Player is on ground and not on a ship, therefore set their added velocity to 0.
                final EntityShipMovementData newMountData = new EntityShipMovementData(null, 0, new Vector3d(), 0);
                draggable.setEntityShipMovementData(newMountData);
            } else {
                if (entity instanceof EntityPlayer) {
                    EntityPlayer player = (EntityPlayer) entity;
                    if (player.isCreative() && player.capabilities.isFlying) {
                        // If the player is flying, then slow down their added velocity significantly every tick
                        final Vector3dc newVelocityAdded = oldVelocityAdded.mul(.95, new Vector3d());
                        final double newYawVelocityAdded = oldYawVelocityAdded * .95 * .95;
                        final EntityShipMovementData newMovementData = oldEntityShipMovementData.withAddedLinearVelocity(newVelocityAdded).withAddedYawVelocity(newYawVelocityAdded);
                        draggable.setEntityShipMovementData(newMovementData);
                    } else {
                        // Otherwise only slow down their added velocity slightly every tick
                        final Vector3dc newVelocityAdded = oldVelocityAdded.mul(.99, new Vector3d());
                        final double newYawVelocityAdded = oldYawVelocityAdded * .95;
                        final EntityShipMovementData newMovementData = oldEntityShipMovementData.withAddedLinearVelocity(newVelocityAdded).withAddedYawVelocity(newYawVelocityAdded);
                        draggable.setEntityShipMovementData(newMovementData);
                    }
                }
            }
        } else {
            float rotYaw = entity.rotationYaw;
            float rotPitch = entity.rotationPitch;
            float prevYaw = entity.prevRotationYaw;
            float prevPitch = entity.prevRotationPitch;

            Vector3dc oldPos = new Vector3d(entity.posX, entity.posY, entity.posZ);

            Matrix4d betweenTransform = ShipTransform.createTransform(
                    lastShipTouchedPlayer.getPrevTickShipTransform(), lastShipTouchedPlayer.getShipTransform());

            ValkyrienUtils.transformEntity(betweenTransform, entity);

            Vector3dc newPos = new Vector3d(entity.posX, entity.posY, entity.posZ);

            // Move the entity back to its old position, the added velocity will be used
            // afterwards
            entity.setPosition(oldPos.x(), oldPos.y(), oldPos.z());
            Vector3dc addedVel = newPos.sub(oldPos, new Vector3d());

            // Now compute the added yaw velocity
            entity.rotationYaw = rotYaw;
            entity.rotationPitch = rotPitch;
            entity.prevRotationYaw = prevYaw;
            entity.prevRotationPitch = prevPitch;

            Vec3d oldLookingVecMc = entity.getLook(1.0f);

            Vector3d oldLookingPos = new Vector3d(oldLookingVecMc.x, oldLookingVecMc.y, oldLookingVecMc.z);
            //            coordTransform.getPrevTickTransform().rotate(oldLookingPos, TransformType.GLOBAL_TO_SUBSPACE);
            //            coordTransform.getCurrentTickTransform().rotate(oldLookingPos, TransformType.SUBSPACE_TO_GLOBAL);
            betweenTransform.transformDirection(oldLookingPos);

            double newPitch = Math.asin(oldLookingPos.y) * -180D / Math.PI;
            double f4 = -Math.cos(-newPitch * 0.017453292D);
            double radianYaw = Math.atan2((oldLookingPos.x / f4), (oldLookingPos.z / f4));
            radianYaw += Math.PI;
            radianYaw *= -180D / Math.PI;


            double yawDif = oldYawVelocityAdded * .99;

            if (!(Double.isNaN(radianYaw) || Math.abs(newPitch) > 85)) {
                double wrappedYaw = MathHelper.wrapDegrees(radianYaw);
                double wrappedRotYaw;
                // We do this because entity.getLook() is calculated differently for EntityLivingBase, it uses
                // rotationYawHead instead of just rotationYaw.

                // if (entity instanceof EntityLivingBase && !(entity instanceof EntityPlayerSP)) {
                // [Changed because EntityPlayerSP is a 'client' class]
                if (entity instanceof EntityLivingBase && !(entity instanceof EntityPlayer)) {
                    wrappedRotYaw = MathHelper.wrapDegrees(entity.getRotationYawHead());
                } else {
                    wrappedRotYaw = MathHelper.wrapDegrees(entity.rotationYaw);
                }
                yawDif = wrappedYaw - wrappedRotYaw;
                if (Math.abs(yawDif) > 180D) {
                    if (yawDif < 0) {
                        yawDif += 360D;
                    } else {
                        yawDif -= 360D;
                    }
                }
                yawDif %= 360D;
                final double threshold = .1D;
                if (Math.abs(yawDif) < threshold) {
                    yawDif = 0D;
                }
            }
            draggable.setEntityShipMovementData(oldEntityShipMovementData.withAddedLinearVelocity(addedVel.mul(1, new Vector3d())).withAddedYawVelocity(yawDif));
        }

        // Now that we've determined the added velocity, move the entity forward by that amount
        final boolean originallySneaking = entity.isSneaking();
        entity.setSneaking(false);

        final EntityShipMovementData newEntityShipMovementData = draggable.getEntityShipMovementData();
        // The added velocity vector of the player, except we have made sure that it won't push the player inside of a
        // solid block.
        final Vector3dc addedVelocityNoNoClip = applyAddedVelocity(newEntityShipMovementData.getAddedLinearVelocity(), entity);
        draggable.setEntityShipMovementData(oldEntityShipMovementData.withAddedLinearVelocity(addedVelocityNoNoClip));

        final double addedYawVelocity = newEntityShipMovementData.getAddedYawVelocity();

        if (!mountData.isMounted()) {
            entity.setRotationYawHead((float) (entity.getRotationYawHead() + addedYawVelocity));
            entity.rotationYaw += addedYawVelocity;
        }

        // Do not add this movement as if the entity were walking it
        // entity.distanceWalkedModified = originalWalked;
        // entity.distanceWalkedOnStepModified = originalWalkedOnStep;
        entity.setSneaking(originallySneaking);
    }

    public static IDraggable getDraggableFromEntity(Entity entity) {
        if (entity == null) {
            return null;
        }
        return (IDraggable) entity;
    }

    public static Entity getEntityFromDraggable(IDraggable draggable) {
        if (draggable == null) {
            return null;
        }
        return (Entity) draggable;
    }

    /**
     * Moves entity forward by addedVelocity, making sure not to clip through blocks
     * @param addedVelocity The velocity added to this entity by the ship they've touched
     * @param entity The entity to be moved
     * @return The vector that the entity actually moved, after detecting collisions with blocks in the world.
     */
    public static Vector3dc applyAddedVelocity(final Vector3dc addedVelocity, final Entity entity) {
        double x = addedVelocity.x();
        double y = addedVelocity.y();
        double z = addedVelocity.z();

        if (entity.isInWeb) {
            entity.isInWeb = false;
            x *= 0.25D;
            y *= 0.05000000074505806D;
            z *= 0.25D;
            entity.motionX = 0.0D;
            entity.motionY = 0.0D;
            entity.motionZ = 0.0D;
        }

        double d2 = x;
        double d3 = y;
        double d4 = z;

        AxisAlignedBB potentialCrashBB = entity.getEntityBoundingBox().offset(x, y, z);

        // TODO: This is a band aid not a solution
        if (potentialCrashBB.getAverageEdgeLength() > 999999) {
            // The player went too fast, something is wrong.
            System.err.println("Entity with ID " + entity.getEntityId()
                    + " went way too fast! Reseting its position.");
            return new Vector3d();
        }

        List<AxisAlignedBB> list1 = entity.world
                .getCollisionBoxes(entity, potentialCrashBB);
        AxisAlignedBB axisalignedbb = entity.getEntityBoundingBox();

        if (y != 0.0D) {
            int k = 0;

            for (int l = list1.size(); k < l; ++k) {
                y = list1.get(k).calculateYOffset(entity.getEntityBoundingBox(), y);
            }

            entity.setEntityBoundingBox(
                    entity.getEntityBoundingBox().offset(0.0D, y, 0.0D));
        }

        if (x != 0.0D) {
            int j5 = 0;

            for (int l5 = list1.size(); j5 < l5; ++j5) {
                x = list1.get(j5).calculateXOffset(entity.getEntityBoundingBox(), x);
            }

            if (x != 0.0D) {
                entity
                        .setEntityBoundingBox(
                                entity.getEntityBoundingBox().offset(x, 0.0D, 0.0D));
            }
        }

        if (z != 0.0D) {
            int k5 = 0;

            for (int i6 = list1.size(); k5 < i6; ++k5) {
                z = list1.get(k5).calculateZOffset(entity.getEntityBoundingBox(), z);
            }

            if (z != 0.0D) {
                entity
                        .setEntityBoundingBox(
                                entity.getEntityBoundingBox().offset(0.0D, 0.0D, z));
            }
        }

        boolean flag = entity.onGround || d3 != y && d3 < 0.0D;

        if (entity.stepHeight > 0.0F && flag && (d2 != x || d4 != z)) {
            double d14 = x;
            double d6 = y;
            double d7 = z;
            AxisAlignedBB axisalignedbb1 = entity.getEntityBoundingBox();
            entity.setEntityBoundingBox(axisalignedbb);
            y = entity.stepHeight;
            List<AxisAlignedBB> list = entity.world
                    .getCollisionBoxes(entity,
                            entity.getEntityBoundingBox().offset(d2, y, d4));
            AxisAlignedBB axisalignedbb2 = entity.getEntityBoundingBox();
            AxisAlignedBB axisalignedbb3 = axisalignedbb2.offset(d2, 0.0D, d4);
            double d8 = y;
            int j1 = 0;

            for (int k1 = list.size(); j1 < k1; ++j1) {
                d8 = list.get(j1).calculateYOffset(axisalignedbb3, d8);
            }

            axisalignedbb2 = axisalignedbb2.offset(0.0D, d8, 0.0D);
            double d18 = d2;
            int l1 = 0;

            for (int i2 = list.size(); l1 < i2; ++l1) {
                d18 = list.get(l1).calculateXOffset(axisalignedbb2, d18);
            }

            axisalignedbb2 = axisalignedbb2.offset(d18, 0.0D, 0.0D);
            double d19 = d4;
            int j2 = 0;

            for (int k2 = list.size(); j2 < k2; ++j2) {
                d19 = list.get(j2).calculateZOffset(axisalignedbb2, d19);
            }

            axisalignedbb2 = axisalignedbb2.offset(0.0D, 0.0D, d19);
            AxisAlignedBB axisalignedbb4 = entity.getEntityBoundingBox();
            double d20 = y;
            int l2 = 0;

            for (int i3 = list.size(); l2 < i3; ++l2) {
                d20 = list.get(l2).calculateYOffset(axisalignedbb4, d20);
            }

            axisalignedbb4 = axisalignedbb4.offset(0.0D, d20, 0.0D);
            double d21 = d2;
            int j3 = 0;

            for (int k3 = list.size(); j3 < k3; ++j3) {
                d21 = list.get(j3).calculateXOffset(axisalignedbb4, d21);
            }

            axisalignedbb4 = axisalignedbb4.offset(d21, 0.0D, 0.0D);
            double d22 = d4;
            int l3 = 0;

            for (int i4 = list.size(); l3 < i4; ++l3) {
                d22 = list.get(l3).calculateZOffset(axisalignedbb4, d22);
            }

            axisalignedbb4 = axisalignedbb4.offset(0.0D, 0.0D, d22);
            double d23 = d18 * d18 + d19 * d19;
            double d9 = d21 * d21 + d22 * d22;

            if (d23 > d9) {
                x = d18;
                z = d19;
                y = -d8;
                entity.setEntityBoundingBox(axisalignedbb2);
            } else {
                x = d21;
                z = d22;
                y = -d20;
                entity.setEntityBoundingBox(axisalignedbb4);
            }

            int j4 = 0;

            for (int k4 = list.size(); j4 < k4; ++j4) {
                y = list.get(j4).calculateYOffset(entity.getEntityBoundingBox(), y);
            }

            entity.setEntityBoundingBox(
                    entity.getEntityBoundingBox().offset(0.0D, y, 0.0D));

            if (d14 * d14 + d7 * d7 >= x * x + z * z) {
                x = d14;
                y = d6;
                z = d7;
                entity.setEntityBoundingBox(axisalignedbb1);
            }
        }

        entity.resetPositionToBB();
        return new Vector3d(x, y, z);
    }
}
