package shadows.fastfurnace.block;

import java.util.Map.Entry;

import net.minecraft.block.BlockFurnace;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry.ItemStackHolder;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.logging.log4j.Level;
import shadows.fastfurnace.FastFurnace;

public class TileFastFurnace extends TileEntityFurnace {

	public static final int INPUT = 0;
	public static final int FUEL = 1;
	public static final int OUTPUT = 2;

	protected ItemStack recipeKey = ItemStack.EMPTY;
	protected ItemStack recipeOutput = ItemStack.EMPTY;
	protected ItemStack failedMatch = ItemStack.EMPTY;

	@ItemStackHolder(value = "minecraft:sponge", meta = 1)
	public static final ItemStack WET_SPONGE = ItemStack.EMPTY;

	private boolean ranFixCheckOnce = false;

	public TileFastFurnace() {
		this.totalCookTime = 200;
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		super.readFromNBT(tag);
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound compound) {
		compound = super.writeToNBT(compound);
		compound.setInteger("BurnTime", this.furnaceBurnTime);
		compound.setInteger("CookTime", this.cookTime);
		compound.setInteger("CookTimeTotal", this.totalCookTime);
		return compound;
	}

	@Override
	public void update() {

		if (world.isRemote && isBurning()) {
			furnaceBurnTime--;
			return;
		} else if (world.isRemote) return;

		ItemStack fuel = ItemStack.EMPTY;
		boolean canSmelt = canSmelt();

		if (!this.isBurning() && !(fuel = furnaceItemStacks.get(FUEL)).isEmpty()) {
			if (canSmelt) burnFuel(fuel, false);
		}

		boolean wasBurning = isBurning();

		if (this.isBurning()) {
			furnaceBurnTime--;
			if (canSmelt) smelt();
			else cookTime = 0;
		}

		if (!this.isBurning()) {
			if (!(fuel = furnaceItemStacks.get(FUEL)).isEmpty()) {
				if (canSmelt()) burnFuel(fuel, wasBurning);
			} else if (cookTime > 0) {
				cookTime = MathHelper.clamp(cookTime - 2, 0, totalCookTime);
			}
		}

		if (wasBurning && !isBurning()) BlockFurnace.setState(false, world, pos);
	}

	protected void smelt() {
		cookTime++;
		if (this.cookTime == this.totalCookTime) {
			this.cookTime = 0;
			this.totalCookTime = this.getCookTime(this.furnaceItemStacks.get(INPUT));
			this.smeltItem();
		}
	}

	protected void burnFuel(ItemStack fuel, boolean burnedThisTick) {
		currentItemBurnTime = (furnaceBurnTime = getItemBurnTime(fuel));
		if (this.isBurning()) {
			Item item = fuel.getItem();
			fuel.shrink(1);
			if (fuel.isEmpty()) furnaceItemStacks.set(FUEL, item.getContainerItem(fuel));
			if (!burnedThisTick) BlockFurnace.setState(true, world, pos);
		}
	}

	protected boolean canSmelt() {
		ItemStack input = furnaceItemStacks.get(INPUT);
		ItemStack output = furnaceItemStacks.get(OUTPUT);
		if (input.isEmpty() || input == failedMatch) {
			//FastFurnace.LOG.log(Level.DEBUG, "Input is empty or failed match in canSmelt()");
			return false;
		}

		if (recipeKey.isEmpty() || !OreDictionary.itemMatches(recipeKey, input, false)) {
			boolean matched = false;
			for (Entry<ItemStack, ItemStack> e : FurnaceRecipes.instance().getSmeltingList().entrySet()) {
				if (OreDictionary.itemMatches(e.getKey(), input, false)) {
					FastFurnace.LOG.log(Level.DEBUG, "Match in canSmelt()! " + input.getDisplayName() + " and " + e.getKey().getDisplayName());
					recipeKey = e.getKey();
					recipeOutput = e.getValue();
					matched = true;
					failedMatch = ItemStack.EMPTY;
					break;
				} else {
					FastFurnace.LOG.log(Level.DEBUG, "No match in canSmelt() between " + input.getDisplayName() + " and " + e.getKey().getDisplayName());
					if(input.getDisplayName().equals(e.getKey().getDisplayName())) {
						FastFurnace.LOG.log(Level.WARN, "Potential missed smelting recipe input match in canSmelt()");
					}
					ItemStack tempStack = input.copy();
					tempStack.removeSubCompound("ForgeCaps");
					if(OreDictionary.itemMatches(e.getKey(),tempStack, false)) {
						FastFurnace.LOG.log(Level.WARN, "Definite missed smelting recipe input match in canSmelt()");
					}
				}
			}
			if (!matched) {
				FastFurnace.LOG.log(Level.DEBUG, "No match in canSmelt()");
				recipeKey = ItemStack.EMPTY;
				recipeOutput = ItemStack.EMPTY;
				failedMatch = input;
				return false;
			}
		}

		if(!ranFixCheckOnce && output.getDisplayName().equals(recipeOutput.getDisplayName())) {
			FastFurnace.LOG.log(Level.WARN, "Incorrect behavior in canSmelt()! recipeOutput.isEmpty(): " + recipeOutput.isEmpty() + ", output.isEmpty(): " + output.isEmpty() + ", recipeOutput name: " + recipeOutput.getDisplayName() + ", output name: " + output.getDisplayName() + ", canItemsStack: " + ItemHandlerHelper.canItemStacksStack(recipeOutput, output));

			if(output.isItemEqual(recipeOutput)) {
				FastFurnace.LOG.log(Level.DEBUG, "Fix in canSmelt() successful! Original furnace output match check from Forge used.");
			}

			//get the output NBT
			NBTTagCompound tempNBT = output.serializeNBT();
			FastFurnace.LOG.log(Level.DEBUG, "Old Output NBT: " + tempNBT);

			//try to make an NBT tag match the recipeOutput NBT
			NBTTagCompound forgeCaps = tempNBT.getCompoundTag("ForgeCaps");
			if(forgeCaps.getSize() == 0 || (FastFurnace.isCustomNPCsLoaded() && forgeCaps.getSize() == 1 && forgeCaps.hasKey("customnpcs:itemscripteddata"))) {
				tempNBT.removeTag("ForgeCaps");
			}
			//THESE MATCH!
			FastFurnace.LOG.log(Level.DEBUG, "RecipeOutput NBT: " + recipeOutput.serializeNBT());
			FastFurnace.LOG.log(Level.DEBUG, "New Output NBT: " + tempNBT);

			//try to make an ItemStack's NBT match the recipeOutput NBT
			ItemStack tempOutput1 = new ItemStack(tempNBT); //adds ForgeCaps tag
			FastFurnace.LOG.log(Level.DEBUG, "tempOutput1 NBT after tag constructor: " + tempOutput1.serializeNBT());
			ItemStack tempOutput2 = new ItemStack(output.getItem(), output.getCount(), output.getMetadata(), null); //adds ForgeCaps tag
			FastFurnace.LOG.log(Level.DEBUG, "tempOutput2 NBT after full constructor: " + tempOutput2.serializeNBT());
			ItemStack tempOutput3 = output.copy(); //adds ForgeCaps tag
			tempOutput3.deserializeNBT(tempNBT);
			FastFurnace.LOG.log(Level.DEBUG, "tempOutput3 NBT after deserialize of tempNBT: " + tempOutput3.serializeNBT());

			//check which fixes worked
			if(ItemHandlerHelper.canItemStacksStack(recipeOutput, tempOutput1)) {
				FastFurnace.LOG.log(Level.DEBUG, "tempOutput1 in canSmelt() successful! Created an ItemStack that matches recipeOutput.");
			}
			if(ItemHandlerHelper.canItemStacksStack(recipeOutput, tempOutput2)) {
				FastFurnace.LOG.log(Level.DEBUG, "tempOutput2 in canSmelt() successful! Created an ItemStack that matches recipeOutput.");
			}
			if(ItemHandlerHelper.canItemStacksStack(recipeOutput, tempOutput3)) {
				FastFurnace.LOG.log(Level.DEBUG, "tempOutput3 in canSmelt() successful! Created an ItemStack that matches recipeOutput.");
			}

			//try to make the recipeOutput NBT match the output NBT.
			ItemStack tempRecipeOutput = recipeOutput.copy(); //adds ForgeCaps tag!
			FastFurnace.LOG.log(Level.DEBUG, "recipeOutput NBT after copy: " + tempRecipeOutput.serializeNBT());
			recipeOutput.deserializeNBT(recipeOutput.serializeNBT()); //doesn't add ForgeCaps tag
			FastFurnace.LOG.log(Level.DEBUG, "recipeOutput NBT after deserialize of itself: " + recipeOutput.serializeNBT());

			if(ItemHandlerHelper.canItemStacksStack(tempRecipeOutput, output)) {
				FastFurnace.LOG.log(Level.DEBUG, "tempRecipeOutput in canSmelt() successful! Copy of recipeOutput matches output.");
			}
			if(ItemHandlerHelper.canItemStacksStack(recipeOutput, output)) {
				FastFurnace.LOG.log(Level.DEBUG, "recipeOutput in canSmelt() successful! recipeOutput changed to match output.");
			}

			ranFixCheckOnce = true;
		}
		return !recipeOutput.isEmpty() && (output.isEmpty() || (ItemHandlerHelper.canItemStacksStack(recipeOutput, output) && (recipeOutput.getCount() + output.getCount() <= output.getMaxStackSize())));
	}

	@Override
	public void smeltItem() {
		ItemStack input = this.furnaceItemStacks.get(INPUT);
		ItemStack recipeOutput = FurnaceRecipes.instance().getSmeltingList().get(recipeKey);
		ItemStack output = this.furnaceItemStacks.get(OUTPUT);

		if (output.isEmpty()) this.furnaceItemStacks.set(OUTPUT, recipeOutput.copy());
		else if (ItemHandlerHelper.canItemStacksStack(output, recipeOutput)) output.grow(recipeOutput.getCount());

		if (input.isItemEqual(WET_SPONGE) && this.furnaceItemStacks.get(FUEL).getItem() == Items.BUCKET) this.furnaceItemStacks.set(FUEL, new ItemStack(Items.WATER_BUCKET));

		input.shrink(1);
	}

	@Override
	public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
		if (oldState.getBlock() == Blocks.FURNACE && newState.getBlock() == Blocks.LIT_FURNACE) return false;
		else if (oldState.getBlock() == Blocks.LIT_FURNACE && newState.getBlock() == Blocks.FURNACE) return false;
		else if (oldState.getBlock() == newState.getBlock()) return false;
		return true;
	}

}
