package net.minecraft.src;

import java.util.List;
import java.util.logging.Logger;

import net.minecraft.client.Minecraft;

public class InvTweaksInventory {

	private static final Logger log = Logger.getLogger("InvTweaks");
	
	public static final int SIZE = 36;
	public static final boolean STACK_NOT_EMPTIED = true;
	public static final boolean STACK_EMPTIED = false;

	private ItemStack[] inventory;
	private int[] rulePriority = new int[SIZE];
	private int[] keywordOrder = new int[SIZE];
	private int[] lockLevels;
	private int clickCount = 0;
	
	// Multiplayer
	private boolean isMultiplayer;
	private PlayerController playerController;
	private EntityPlayerSP player;
	
	public InvTweaksInventory(Minecraft minecraft, int[] lockLevels) {

		this.inventory = minecraft.thePlayer.inventory.mainInventory;
		this.playerController = minecraft.playerController;
		this.player = minecraft.thePlayer;
		this.isMultiplayer = minecraft.isMultiplayerWorld();
		this.lockLevels = lockLevels;
		
		for (int i = 0; i < SIZE; i++) {
			this.rulePriority[i] = -1;
			if (this.inventory[i] != null) {
				this.keywordOrder[i] = getItemOrder(
						this.inventory[i].itemID,
						this.inventory[i].getItemDamage());
			}
		}
		
	}
	
	/**
	 * Tries to move a stack from i to j, and swaps them
	 * if j is already occupied but i is of grater priority
	 * (even if they are of same ID).
	 * CONTRACT: i slot must not be null.
	 * @param i from slot
	 * @param j to slot
	 * @param priority The rule priority. Use 1 if the stack was not moved using a rule.
	 * @return true if it has been done successfully.
	 */
	public boolean moveStack(int i, int j, int priority) {
		
		if (getLockLevel(i) <= priority) {
		
			if (i == j) {
				markAsMoved(i, priority);
				return true;
			}
			
			boolean targetEmpty = inventory[j] == null;
			
			// Move to empty slot
			if (targetEmpty && lockLevels[j] <= priority) {
				swapOrMerge(i, j, priority);
				return true;
			}

			// Try to swap/merge
			else if (!targetEmpty) {
				boolean canBeSwapped = false;
				if (lockLevels[j] <= priority) {
					if (rulePriority[j] < priority) {
						canBeSwapped = true;
					}
					else if (rulePriority[j] == priority) {
						if (isOrderedBefore(i, j)) {
							canBeSwapped = true;
						}
					}
				}
				if (canBeSwapped || canBeMerged(i, j)) {
					swapOrMerge(i, j, priority);
					return true;
				}
			}
			
		}
		
		return false;
	}

	/**
	 * Merge from stack i to stack j, only if i is not under a greater lock than j.
	 * @param i from slot
	 * @param j to slot
	 * @return STACK_NOT_EMPTIED if items remain in i, STACK_EMPTIED otherwise.
	 */
	public boolean mergeStacks(int i, int j) {
		if (lockLevels[i] <= lockLevels[j]) {
			return swapOrMerge(i, j, 1) ? STACK_EMPTIED : STACK_NOT_EMPTIED;
		}
		else {
			return STACK_NOT_EMPTIED;
		}
	}

	public boolean hasToBeMoved(int slot) {
		return inventory[slot] != null && rulePriority[slot] == -1;
	}

	/**
	 * Note: asserts stacks are not null
	 */
	public boolean areSameItem(ItemStack stack1, ItemStack stack2) {
		// Note: may be invalid if a stackable item can take damage
		// (currently never the case in vanilla, an never should be)
		return stack1.itemID == stack2.itemID
				&& (stack1.getItemDamage() == stack2.getItemDamage() // same item variant
						|| stack1.getMaxStackSize() == 1); // except if unstackable
	}

	public boolean canBeMerged(int i, int j) {
		return (i != j && inventory[i] != null && inventory[j] != null && 
				areSameItem(inventory[i], inventory[j]) &&
				inventory[j].stackSize < inventory[j].getMaxStackSize());
	}

	public boolean isOrderedBefore(int i, int j) {
		
		if (inventory[j] == null)
			return true;
		else if (inventory[i] == null || keywordOrder[i] == -1)
			return false;
		else {
			if (keywordOrder[i] == keywordOrder[j]) {
				// Items of same keyword orders can have different IDs,
				// in the case of categories defined by a range of IDs
				if (inventory[i].itemID == inventory[j].itemID) {
					if (inventory[i].stackSize == inventory[j].stackSize) {
						return inventory[i].getItemDamage() < inventory[j].getItemDamage()
								&& (!isMultiplayer || inventory[j].getMaxStackSize() > 1); // exclude tools
					}
					else {
						return inventory[i].stackSize > inventory[j].stackSize;
					}
				}
				else {
					return inventory[i].itemID > inventory[j].itemID;
				}
			}
			else {
				return keywordOrder[i] < keywordOrder[j];
			}
		}
	}

	/**
	 * Swaps two stacks, i.e. clicks to i, then j, then back to i if necessary.
	 * If the stacks are able to be merged, the biggest part will then be in j.
	 * @param i
	 * @param j
	 * @return true if i is now empty
	 * 
	 */
	public boolean swapOrMerge(int i, int j, int priority) {
		
		// Merge stacks
		if (canBeMerged(i, j)) {
			
			int sum = inventory[i].stackSize + inventory[j].stackSize;
			int max = inventory[j].getMaxStackSize();
			
			if (sum <= max) {
				
				remove(i);
				if (isMultiplayer)
					click(i);

				put(inventory[j], j, priority);
				if (isMultiplayer)
					click(j);
				else
					inventory[j].stackSize = sum;
				return true;
			}
			else {
				if (isMultiplayer) {
					click(i);
					click(j);
					click(i);
				}
				else {
					inventory[i].stackSize = sum - max;
					inventory[j].stackSize = max;
				}
				put(inventory[j], j, priority);
				return false;
			}
		}
		
		// Swap stacks
		else {
			
			// i to j
			ItemStack jStack = inventory[j];
			ItemStack iStack = remove(i);
			if (isMultiplayer) {
				click(i);
				click(j);
			}
			put(iStack, j, priority);
			
			// j to i
			if (jStack != null) {
				int dropSlot = i;
				if (lockLevels[j] > lockLevels[i]) {
					for (int k = 0; k < SIZE; k++) {
						if (inventory[k] == null && lockLevels[k] == 0) {
							dropSlot = k;
							break;
						}
					}
				}
				if (isMultiplayer) {
					click(dropSlot);
				}
				put(jStack, dropSlot, -1);
				return false;
			}
			else {
				return true;
			}
		}
	}

	public void markAsMoved(int i, int priority) {
		rulePriority[i] = priority;
	}

	public void markAsNotMoved(int i) {
		rulePriority[i] = -1;
	}

	/**
	 * If an item is in hand (= attached to the cursor), puts it down.
	 * @return false if there is no room to put the item.
	 */
	public boolean putSelectedItemDown() {
		ItemStack selectedStack = player.inventory.getItemStack();
		if (selectedStack != null) {
			// Try to find an unlocked slot first, to avoid
			// impacting too much the sorting
			for (int step = 1; step <= 2; step++) {
				for (int i = SIZE-1; i >= 0; i--) {
					if (inventory[i] == null
							&& (lockLevels[i] == 0 || step == 2)) {
						if (isMultiplayer) {
							click(i);
						}
						else {
							inventory[i] = selectedStack;
							player.inventory.setItemStack(null);
						}
						return true;
					}
				}
			}
			return false;
		}
		return true;
	}

	public int getClickCount() {
		if (isMultiplayer) {
			return clickCount;
		}
		else
			return -1;
	}

	public ItemStack getItemStack(int i) {
		return inventory[i];
	}
	
	public int getLockLevel(int i) {
		return lockLevels[i];
	}

	/**
	 * Alternative to InvTweaksInventory.SIZE
	 */
	public int getSize() {
		return SIZE;
	}
	
	/**
	 * (Multiplayer only)
	 * Click on the interface. Slower than manual swapping, but works in multiplayer.
	 * @param slot The targeted slot
	 * @param priority Ignored
	 * @param oldSlot The stacks previous spot
	 * @param stack The stack that was in the slot before the operation
	 */
	public void click(int slot) {
		clickCount++;
		
		if (log.getLevel() == InvTweaks.DEBUG)
			log.info("Click on "+slot);
		
		// After clicking, we'll need to wait for server answer before continuing.
		// We'll do this by listening to any change in the slot, but this implies we
		// check first if the click will indeed produce a change.
		boolean uselessClick = false;
		ItemStack stackInSlot = (inventory[slot] != null) ? inventory[slot].copy() : null;
		ItemStack stackInHand = player.inventory.getItemStack();
		
		// Useless if empty stacks
		if (stackInHand == null && stackInSlot == null)
			uselessClick = true;
		// Useless if destination stack is full
		else if (stackInHand != null && stackInSlot != null &&
				areSameItem(stackInHand, stackInSlot) &&
				stackInSlot.stackSize == stackInSlot.getMaxStackSize()) {
			uselessClick = true;
		}
		
		// Click!
		playerController.func_27174_a(
				player.craftingInventory.windowId, // Select active inventory
				((slot > 8) ? slot - 9 : slot + 27) + 
					player.craftingInventory.slots.size() - 36, // Targeted slot
						// (converted for the network protocol indexes,
						// see http://mc.kev009.com/Inventory#Windows)
				0, // Left-click
				false, // Shift not held 
				player
			);
		
		// Wait for inventory update
		if (!uselessClick) {
			int pollingTime = 0;
			while (ItemStack.areItemStacksEqual(inventory[slot], stackInSlot)
					&& pollingTime < InvTweaks.POLLING_TIMEOUT) {
				InvTweaks.trySleep(InvTweaks.POLLING_DELAY);
				pollingTime += InvTweaks.POLLING_DELAY;
			}
			if (pollingTime >= InvTweaks.POLLING_TIMEOUT)
				log.warning("Click timout");
		}
	}
	
	/**
	 * SP: Removes the stack from the given slot
	 * SMP: Registers the action without actually doing it.
	 * @param slot
	 * @return The removed stack
	 */
	private ItemStack remove(int slot) {
		ItemStack removed = inventory[slot];
		if (log.getLevel() == InvTweaks.DEBUG) {
			try {
				log.info("Removed: "+InvTweaksTree.getItems(
						removed.itemID, removed.getItemDamage()).get(0)+" from "+slot);
			}
			catch (NullPointerException e) {
				log.info("Removed: null from "+slot);
			}
		}
		if (!isMultiplayer) {
			inventory[slot] = null;
		}
		rulePriority[slot] = -1;
		keywordOrder[slot] = -1;
		return removed;
	}
	
	/**
	 * SP: Puts a stack in the given slot. WARNING: Any existing stack will be overriden!
	 * SMP: Registers the action without actually doing it.
	 * @param stack
	 * @param slot
	 * @param priority
	 */
	private void put(ItemStack stack, int slot, int priority) {
		if (log.getLevel() == InvTweaks.DEBUG) {
			try {
				log.info("Put: "+InvTweaksTree.getItems(
						stack.itemID, stack.getItemDamage()).get(0)+" in "+slot);
			}
			catch (NullPointerException e) {
				log.info("Removed: null");
			}
		}
		if (!isMultiplayer) {
			inventory[slot] = stack;
		}
		rulePriority[slot] = priority;
		keywordOrder[slot] = getItemOrder(stack.itemID, stack.getItemDamage());
	}
	
	private int getItemOrder(int itemID, int itemDamage) {
		List<InvTweaksItem> items = InvTweaksTree.getItems(itemID, itemDamage);
		return (items != null && items.size() > 0)
				? items.get(0).getOrder()
				: Integer.MAX_VALUE;
	}
}
