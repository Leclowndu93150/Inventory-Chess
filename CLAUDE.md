# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GUIChess is a Minecraft NeoForge mod that implements a complete chess system within Minecraft. Players can challenge each other, play against AI bots, spectate games, and track statistics through an inventory-based GUI system.

## Build and Development Commands

```bash
# Build the mod
./gradlew build

# Run development client
./gradlew runClient

# Run development server
./gradlew runServer

# Generate data (models, textures, etc.)
./gradlew runData

# Clean build artifacts
./gradlew clean
```

## Architecture Overview

### Core Components

- **GameManager**: Singleton coordinator managing all chess activities, game lifecycle, player data, and concurrent operations
- **ChessBoard**: Core chess engine implementing game rules, move validation, and state management
- **ChessGUI**: Main player interface using SGui library for inventory-based chess board
- **StockfishIntegration**: External chess engine integration for hints and analysis

### Key Packages

- `game/`: Core game logic (GameManager, ChessGame, ChessBoard, bot games)
- `gui/`: All user interfaces (ChessGUI, spectator GUI, challenge flows)
- `chess/`: Chess domain objects (pieces, moves, positions, game state)
- `engine/`: Stockfish integration and position analysis
- `command/`: All chess-related commands (/chess play, /chess bot, etc.)
- `data/`: Player statistics and persistence
- `util/`: Helpers for sounds, overlays, time management

### GUI System

Uses **SGui library** (eu.pb4.sgui) for inventory-based interfaces:
- ChessGUI extends SimpleGui for the main game board
- Uses GuiElementBuilder with custom model data for pieces and squares
- Inventory slots map to chess board positions with coordinate transformation

#### SGui Library Architecture

The project includes a local copy of the SGui library in `src/sguilib/`. Key components:

**Core GUI Classes:**
- `SimpleGui`: Base class for slot-based GUIs (extends BaseSlotGui)
- `GuiElementBuilder`: Fluent builder for creating GUI elements with ItemStacks
- `GuiElement`: Individual GUI elements with callbacks and display items
- `BaseSlotGui`: Abstract base providing slot management and player interaction

**Key SGui Patterns:**
```java
// Creating a GUI
public class ChessGUI extends SimpleGui {
    public ChessGUI(ServerPlayer player, MenuType<?> type) {
        super(type, player, true); // true = include player inventory
    }
}

// Building GUI elements
GuiElementBuilder builder = new GuiElementBuilder(Items.GRAY_DYE)
    .setCustomModelData(modelData)
    .setName(Component.literal("Chess Piece"))
    .setCallback((index, type, action, gui) -> handleClick());

// Setting slots
setSlot(slotIndex, builder);
```

**Virtual System:**
- `VirtualScreenHandler`: Manages server-side GUI state
- `VirtualSlot`: Individual slot implementations
- `SguiScreenHandlerFactory`: Creates screen handlers for clients

**Element System:**
- `GuiElementInterface`: Base interface for all GUI elements
- `ClickCallback`: Handles player interactions with GUI elements
- `ItemClickCallback`: Legacy callback interface for backwards compatibility

### Data Persistence

- Player data saved to `world/chess_data/player_data.nbt`
- Game history stored in `world/chess_data/game_history/`
- ELO ratings, win/loss records, and preferences tracked per player

### Stockfish Integration

- Downloads and manages Stockfish engine automatically
- Provides hints, analysis, and bot opponents
- Configurable skill levels and analysis depth
- Handles cross-platform executable management

## Key Patterns

### Board Coordinate System
- ChessPosition uses file (0-7, a-h) and rank (0-7, 1-8) coordinates
- GUI transforms coordinates based on player perspective (white/black view)
- BoardSquare enum handles visual states (light/dark, selected, valid moves)

### Piece Overlays
- Uses custom model data on gray_dye items for all chess pieces
- PieceOverlayHelper manages visual states (selected, capturable, in-check)
- OverlayModelDataRegistry maps overlay keys to model data values

### Game State Management
- GameState enum tracks current game phase (turns, check, checkmate, draws)
- ChessBoard validates moves and detects game endings
- GameManager coordinates between GUI updates and game logic

### Time Control
- TimeControl enum defines game time formats (blitz, rapid, bullet)
- Timer system with automatic forfeit on timeout
- Visual time warnings for players

## Important Development Notes

- All GUI interactions are asynchronous and thread-safe
- Player inventories are saved/restored during games
- Games support spectators with real-time updates
- Challenge system includes betting with item stakes
- Bot games use Stockfish engine with configurable difficulty
- Analysis mode available for post-game review

## Testing

Run the development server and use `/chess` commands to test functionality:
- `/chess bot` for AI games
- `/chess challenge` for player challenges  
- `/chess board` to open practice board
- `/chess stats` for player statistics