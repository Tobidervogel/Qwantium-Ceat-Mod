# Qwantium PvP Bot bridge for Quantum's PvP Practice map.
# Hands out the bot/target tags like the map does, except that a player who turned on Quantum
# control (/qbot quantum on, tag qbot_quantum) plays the bot instead of the map's fake bots.
tag @a remove xlib_bot
tag @a remove xlib_target
execute if entity @a[tag=qbot_quantum] run tag @a[tag=qbot_quantum] add xlib_bot
execute unless entity @a[tag=qbot_quantum] run tag @a[name=quantumbot] add xlib_bot
execute unless entity @a[tag=qbot_quantum] run tag @a[name=Quantum] add xlib_bot
execute unless entity @a[tag=qbot_quantum] run tag @a[name=Notch] add xlib_bot
execute unless entity @a[tag=qbot_quantum] run tag @a[name=Herobrine] add xlib_bot
tag @a[tag=!qbot_quantum,name=!quantumbot,name=!Notch,name=!Quantum,name=!Herobrine,gamemode=!spectator] add xlib_target
