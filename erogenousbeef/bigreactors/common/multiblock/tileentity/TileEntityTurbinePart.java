package erogenousbeef.bigreactors.common.multiblock.tileentity;

import java.io.DataInputStream;
import java.io.IOException;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraftforge.common.ForgeDirection;
import erogenousbeef.core.common.CoordTriplet;
import erogenousbeef.core.multiblock.MultiblockControllerBase;
import erogenousbeef.core.multiblock.MultiblockTileEntityBase;
import erogenousbeef.core.multiblock.MultiblockValidationException;
import erogenousbeef.bigreactors.client.gui.GuiTurbineController;
import erogenousbeef.bigreactors.common.multiblock.MultiblockTurbine;
import erogenousbeef.bigreactors.common.multiblock.block.BlockTurbinePart;
import erogenousbeef.bigreactors.common.multiblock.interfaces.IMultiblockGuiHandler;
import erogenousbeef.bigreactors.common.multiblock.interfaces.IMultiblockNetworkHandler;
import erogenousbeef.bigreactors.gui.container.ContainerSlotless;
import erogenousbeef.bigreactors.net.PacketWrapper;
import erogenousbeef.bigreactors.net.Packets;

public class TileEntityTurbinePart extends MultiblockTileEntityBase implements IMultiblockGuiHandler, IMultiblockNetworkHandler {

	public enum PartPosition {
		Unknown,
		Interior,
		FrameCorner,
		Frame,
		TopFace,
		BottomFace,
		NorthFace,
		SouthFace,
		EastFace,
		WestFace
	}

	protected PartPosition partPosition;
	protected ForgeDirection outwardsDirection;
	
	protected int _metadata;
	
	public TileEntityTurbinePart() {
		partPosition = PartPosition.Unknown;
		outwardsDirection = ForgeDirection.UNKNOWN;
		_metadata = -1;
	}
	
	public TileEntityTurbinePart(int metadata) {
		this();
		_metadata = metadata;
	}

	private int getMetadata() {
		if(_metadata < 0) {
			_metadata = worldObj.getBlockMetadata(xCoord, yCoord, zCoord);
		}
		
		return _metadata;
	}
	
	@Override
	public MultiblockControllerBase createNewMultiblock() {
		return new MultiblockTurbine(worldObj);
	}

	public ForgeDirection getOutwardsDir() {
		return outwardsDirection;
	}
	
	public PartPosition getPartPosition() {
		return partPosition;
	}
	
	@Override
	public Class<? extends MultiblockControllerBase> getMultiblockControllerType() {
		return MultiblockTurbine.class;
	}

	@Override
	public void isGoodForFrame() throws MultiblockValidationException {
		if(getMetadata() != BlockTurbinePart.METADATA_HOUSING) {
			throw new MultiblockValidationException(String.format("%d, %d, %d - only turbine housing may be used as part of the turbine's frame", xCoord, yCoord, zCoord));
		}
	}

	@Override
	public void isGoodForSides() {
	}

	@Override
	public void isGoodForTop() {
	}

	@Override
	public void isGoodForBottom() {
	}

	@Override
	public void isGoodForInterior() throws MultiblockValidationException {
		if(getMetadata() != BlockTurbinePart.METADATA_HOUSING) {
			throw new MultiblockValidationException(String.format("%d, %d, %d - this part is not valid for the interior of a turbine", xCoord, yCoord, zCoord));
		}
	}

	@Override
	public void onMachineAssembled(MultiblockControllerBase controller) {
		CoordTriplet maxCoord = controller.getMaximumCoord();
		CoordTriplet minCoord = controller.getMinimumCoord();
		
		// Discover where I am on the reactor
		outwardsDirection = ForgeDirection.UNKNOWN;
		partPosition = PartPosition.Unknown;

		int facesMatching = 0;
		if(maxCoord.x == this.xCoord || minCoord.x == this.xCoord) { facesMatching++; }
		if(maxCoord.y == this.yCoord || minCoord.y == this.yCoord) { facesMatching++; }
		if(maxCoord.z == this.zCoord || minCoord.z == this.zCoord) { facesMatching++; }
		
		if(facesMatching <= 0) { partPosition = PartPosition.Interior; }
		else if(facesMatching >= 3) { partPosition = PartPosition.FrameCorner; }
		else if(facesMatching == 2) { partPosition = PartPosition.Frame; }
		else {
			// 1 face matches
			if(maxCoord.x == this.xCoord) {
				partPosition = PartPosition.EastFace;
				outwardsDirection = ForgeDirection.EAST;
			}
			else if(minCoord.x == this.xCoord) {
				partPosition = PartPosition.WestFace;
				outwardsDirection = ForgeDirection.WEST;
			}
			else if(maxCoord.z == this.zCoord) {
				partPosition = PartPosition.SouthFace;
				outwardsDirection = ForgeDirection.SOUTH;
			}
			else if(minCoord.z == this.zCoord) {
				partPosition = PartPosition.NorthFace;
				outwardsDirection = ForgeDirection.NORTH;
			}
			else if(maxCoord.y == this.yCoord) {
				partPosition = PartPosition.TopFace;
				outwardsDirection = ForgeDirection.UP;
			}
			else {
				partPosition = PartPosition.BottomFace;
				outwardsDirection = ForgeDirection.DOWN;
			}
		}
		
		// Re-render this block on the client
		if(worldObj.isRemote) {
			this.worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}
	}

	@Override
	public void onMachineBroken() {
		partPosition = PartPosition.Unknown;
		outwardsDirection = ForgeDirection.UNKNOWN;
		
		// Re-render this block on the client
		if(worldObj.isRemote) {
			this.worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}
	}

	@Override
	public void onMachineActivated() {
		// Re-render controller as active state has changed
		if(worldObj.isRemote && getMetadata() == BlockTurbinePart.METADATA_CONTROLLER) {
			this.worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}
	}

	@Override
	public void onMachineDeactivated() {
		// Re-render controller as active state has changed
		if(worldObj.isRemote && getMetadata() == BlockTurbinePart.METADATA_CONTROLLER) {
			this.worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}
	}
	
	///// Network communication - IMultiblockNetworkHandler

	@Override
	public void onNetworkPacket(int packetType, DataInputStream data) throws IOException {
		if(!this.isConnected()) {
			return;
		}

		// Client->Server Packets
		if(packetType == Packets.MultiblockControllerButton) {
			Class decodeAs[] = { String.class, Boolean.class };
			Object[] decodedData = PacketWrapper.readPacketData(data, decodeAs);
			String buttonName = (String) decodedData[0];
			boolean newValue = (Boolean) decodedData[1];
			
			if(buttonName.equals("activate")) {
				FMLLog.info("setting turbine controller active: %b", newValue);
				getTurbine().setActive(newValue);
			}
		}

		// Server->Client Packets
		if(packetType == Packets.MultiblockTurbineFullUpdate) {
			getTurbine().onReceiveUpdatePacket(data);
		}
	}

	/// GUI Support - IMultiblockGuiHandler
	/**
	 * @return The Container object for use by the GUI. Null if there isn't any.
	 */
	@Override
	public Object getContainer(InventoryPlayer inventoryPlayer) {
		if(!this.isConnected()) {
			return null;
		}
		
		if(getMetadata() == BlockTurbinePart.METADATA_CONTROLLER) {
			return (Object)(new ContainerSlotless(getTurbine(), inventoryPlayer.player));
		}
		
		return null;
	}
	
	@SideOnly(Side.CLIENT)
	@Override
	public Object getGuiElement(InventoryPlayer inventoryPlayer) {
		if(!this.isConnected()) {
			return null;
		}

		if(getMetadata() == BlockTurbinePart.METADATA_CONTROLLER) {
			return new GuiTurbineController((Container)getContainer(inventoryPlayer), this);
		}
		return null;
	}

	public MultiblockTurbine getTurbine() {
		return (MultiblockTurbine)getMultiblockController();
	}
}
