package it.uniroma2.dicii.isw2.ml.impl;

/**
 * What an effort-aware ranking needs to know of one row a model was asked about: how likely the model
 * thought it buggy, how much it costs to inspect, and whether it was buggy after all.
 *
 * @param buggyProbability the probability the model gave the buggy class, in the fold that held the row
 *                         out
 * @param size             how much inspecting the class costs, in the unit of the size attribute
 * @param buggy            whether the class was buggy
 */
record RankedRow(double buggyProbability, double size, boolean buggy) {
}
