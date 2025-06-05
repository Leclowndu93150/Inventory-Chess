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
- `AnimatedGuiElement`: Multi-frame animated elements with timing control
- `GuiElementBuilderInterface`: Base interface for all element builders

**Click Type System:**
- `ClickType`: Simplified enum for common click types (LEFT, RIGHT, SHIFT+LEFT, etc.)
- Event flow: Player click → VirtualScreenHandler → ClickType conversion → Callback execution
- GUI can override `onClick()` for additional handling

**Virtual Screen Architecture:**
- `VirtualScreenHandler`: Server-side container menu implementation
- `VirtualSlot`: Read-only slot implementation for GUI elements  
- `VirtualInventory`: Dummy inventory backing virtual slots
- Benefits: Isolation, security, flexibility with mixed virtual/real slots

**GUI Hierarchy:**
```
GuiInterface
├── SlotGuiInterface
    ├── BaseSlotGui (abstract base class)
        ├── SimpleGui (main implementation)
        └── LayeredGui (multi-layer support)
    ├── AnvilInputGui (specialized input GUI)
    ├── BookGui (book-based GUI)
    └── MerchantGui (trading interface)
```

**Best Practices:**
- Use builder pattern for element creation with fluent API
- Handle click types appropriately (typically check for `type.isLeft`)
- Disable auto-update during rapid changes for performance
- Use custom model data for visual states and piece appearance
- Batch slot updates when possible for efficiency

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

### Debug Commands (Admin level 2+)
- `/chess admin debug simple` - Opens single chest GUI (9x3 = 27 slots) with numbered glass panes showing slot indices
- `/chess admin debug double` - Opens double chest GUI (9x6 = 54 slots) with numbered glass panes showing slot indices
- `/chess admin testoverlay` - Tests overlay model data assignments

These debug GUIs help understand slot mapping and are essential for GUI development and troubleshooting layout issues.

## Coding Style Guidelines

### JavaDoc Standards
- **Class-level documentation only**: Document the purpose and responsibility of classes
- **Public method documentation**: Only for main public methods that represent key functionality
- **No internal documentation**: Do not document private methods, implementation details, or obvious getters/setters
- **Concise and focused**: Keep documentation brief and focused on the "what" and "why", not the "how"

### JavaDoc Format
```java
/**
 * Brief description of what the class does.
 * 
 * <p>Additional context or important notes about the class responsibility.
 * 
 * <p><strong>Important warnings or notes in uppercase when needed.</strong>
 */
public class ExampleClass {
    
    /**
     * Brief description of what this method accomplishes.
     * 
     * @param param description if not obvious from name
     * @return description if not obvious from type
     */
    public ReturnType mainMethod(ParamType param) {
        // Implementation without internal comments
    }
    
    // No JavaDoc for simple/obvious methods
    public void simpleMethod() {
    }
    
    private void helperMethod() {
        // No JavaDoc for private methods
    }
}
```

### Package Organization
- **Logical grouping**: Related classes in dedicated sub-packages
- **Single responsibility**: Each package has a clear, focused purpose
- **Hierarchical structure**: Sub-packages for different aspects of functionality

#### Current Package Structure
```
com.leclowndu93150.guichess/
├── engine/
│   ├── integration/ - Stockfish integrations (web and binary)
│   ├── installer/ - Binary installation and management
│   └── analysis/ - Position analysis and evaluation
├── game/
│   ├── core/ - Core game logic (ChessGame, ChessBoard, GameManager)
│   ├── players/ - Player implementations (Human, Bot, etc.)
│   └── challenge/ - Challenge system and flow
├── gui/
│   ├── game/ - Game interfaces (ChessGUI, SpectatorGUI)
│   ├── challenge/ - Challenge creation and acceptance GUIs
│   ├── analysis/ - Analysis and practice GUIs
│   └── debug/ - Debug and development GUIs
├── data/
│   ├── storage/ - Data persistence and storage
│   └── models/ - Data models and structures
└── util/
    ├── audio/ - Sound management
    ├── visual/ - Visual helpers and overlays
    └── time/ - Time management utilities
```

### Code Cleanliness
- **Remove useless comments**: No obvious or redundant comments
- **Clean imports**: Organize and remove unused imports
- **Consistent naming**: Clear, descriptive variable and method names
- **No dead code**: Remove unused methods and variables

### Best Practices
- Keep classes focused on single responsibilities
- Use builder patterns for complex object creation
- Handle errors gracefully without verbose logging in production code
- Prefer composition over inheritance where appropriate
- Use enums for fixed sets of constants