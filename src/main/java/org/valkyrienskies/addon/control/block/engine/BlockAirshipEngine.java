/*
 * Adapted from the Wizardry License
 *
 * Copyright (c) 2015-2018 the Valkyrien Warfare team
 *
 * Permission is hereby granted to any persons and/or organizations using this software to copy, modify, merge, publish, and distribute it.
 * Said persons and/or organizations are not allowed to use the software or any derivatives of the work for commercial use or any other means to generate income unless it is to be used as a part of a larger project (IE: "modpacks"), nor are they allowed to claim this software as their own.
 *
 * The persons and/or organizations are also disallowed from sub-licensing and/or trademarking this software without explicit permission from the Valkyrien Warfare team.
 *
 * Any persons and/or organizations using this software must disclose their source code and have it publicly available, include this license, provide sufficient credit to the original authors of the project (IE: The Valkyrien Warfare team), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package org.valkyrienskies.addon.control.block.engine;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.valkyrienskies.addon.control.tileentity.TileEntityPropellerEngine;
import org.valkyrienskies.mod.common.block.IBlockForceProvider;
import org.valkyrienskies.mod.common.math.Vector;
import org.valkyrienskies.mod.common.physics.management.PhysicsObject;

/**
 * All engines should extend this class, that way other kinds of engines can be made without making
 * tons of new classes for them. Only engines that add new functionality should have their own
 * class.
 */
public abstract class BlockAirshipEngine extends Block implements IBlockForceProvider,
    ITileEntityProvider {

    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    protected double enginePower;

    public BlockAirshipEngine(Material materialIn, double enginePower) {
        super(materialIn);
        this.setEnginePower(enginePower);
    }

    @Override
    public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing,
        float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        return this.getDefaultState()
            .withProperty(FACING, EnumFacing.getDirectionFromEntityLiving(pos, placer));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        // &7 to remove any higher bits
        return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta & 7));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        int i = state.getValue(FACING).getIndex();
        return i;
    }

    @Override
    public Vector getBlockForceInShipSpace(World world, BlockPos pos, IBlockState state,
        PhysicsObject physicsObject, double secondsToApply) {
        Vector acting = new Vector(0, 0, 0);
        if (!world.isBlockPowered(pos)) {
            return acting;
        }

        TileEntity tileEntity = world.getTileEntity(pos);
        if (tileEntity instanceof TileEntityPropellerEngine) {
            //Just set the Thrust to be the maximum
            ((TileEntityPropellerEngine) tileEntity)
                .setThrustMultiplierGoal(this.getEnginePower(world, pos, state, physicsObject));
            ((TileEntityPropellerEngine) tileEntity).updateTicksSinceLastRecievedSignal();
            ((TileEntityPropellerEngine) tileEntity).setThrustMultiplierGoal(1D);
            return ((TileEntityPropellerEngine) tileEntity)
                .getForceOutputUnoriented(secondsToApply, physicsObject);
        }

        return acting;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean canConnectRedstone(IBlockState state, IBlockAccess world, BlockPos pos,
        EnumFacing face) {
        return true;
    }

    /**
     * Used for calculating force applied to the airship by an engine. Override this in your
     * subclasses to make engines that are more dynamic than simply being faster engines.
     */
    public double getEnginePower(World world, BlockPos pos, IBlockState state,
        PhysicsObject physicsObject) {
        return this.enginePower;
    }

    public TileEntity createNewTileEntity(World worldIn, int meta) {
        IBlockState state = getStateFromMeta(meta);
        return new TileEntityPropellerEngine(new Vector(state.getValue(FACING)), true, enginePower);
    }

    public void setEnginePower(double power) {
        this.enginePower = power;
    }

    /**
     * The spinning rotor engines must obey the rules of airflow, otherwise idiots would stick all
     * their engines inside leaving nothing exposed.
     */
    @Override
    public boolean doesForceSpawnParticles() {
        return true;
    }
}
