package org.nrg.hcp.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * @author MRH
 *
 * Computes scores from ASR Raw data.  Returns a map of scores from a map of raw values
 */
public class ASRScoringUtil {
	
   	private static final String raw_prepend = "ASRVIII.ASR_";
	// Anxiety & Depression
	private static final String[] ANXDP_ITEMS = new String[] {"12","13","14","22","31","33","34","35","45","47","50","52","71","91","103","107","112","113"};
	// Withdrawn
	private static final String[] WTHDP_ITEMS = new String[] {"25","30","42","48","60","65","67","69","111"};
	// Somatic Complaints
	private static final String[] SOM_ITEMS = new String[] {"51","54","56a","56b","56c","56d","56e","56f","56g","56h","56i","100"};
	// Thought Problems
    private static final String[] THO_ITEMS = new String[] {"9","18","36","40","46","63","66","70","84","85"};
	// Attention Problems
    private static final String[] ATT_ITEMS = new String[] {"1","8","11","17","53","59","61","64","78","101","102","105","108","119","121"};
	// Aggressive Behavior 
    private static final String[] AGG_ITEMS = new String[] {"3","5","16","28","37","55","57","68","81","86","87","95","97","116","118"};
	// Rule-breaking behavior
    private static final String[] RULE_ITEMS = new String[] {"6","20","23","26","39","41","43","76","82","90","92","114","117","122"};
	// Intrusive
    private static final String[] INT_ITEMS = new String[] {"7","19","74","93","94","104"};
	// Other problems
    private static final String[] OTHER_ITEMS = new String[] {"10","21","24","27","29","32","38","44","58","62","72","75","77","79","83","89","96","99","110","115","120"};
	// Critical Items Sum
    private static final String[] CRITICAL_ITEMS = new String[] {"14","91","103","9","18","40","66","70","84","8","16","55","57","97","6","90","92","10","21"};
	// Computed internalizing sum
    private static final String[] COMP_INTERNALIZING_ITEMS = new String[] {"ASR_anxdp_raw","ASR_wthdp_raw","ASR_som_raw"};
	// Computed internalizing sum
    private static final String[] COMP_EXTERNALIZING_ITEMS = new String[] {"ASR_agg_raw","ASR_rule_raw","ASR_int_raw"};
	// Computed internalizing sum
    private static final String[] COMP_OTHER_ITEMS = new String[] {"ASR_tho_raw","ASR_att_raw","ASR_other_raw"};
	// Computed internalizing sum
    private static final String[] COMP_TOTAL_ITEMS = new String[] {"ASR_computed_internalizing_raw","ASR_computed_externalizing_raw","ASR_computed_other_raw"};
    // DSM Depressive Problems
    private static final String[] DSM_DEP_ITEMS = new String[] {"14","18","24","35","52","54","60","77","78","91","100","102","103","107"};
    // DSM Anxiety Problems
    private static final String[] DSM_ANX_ITEMS = new String[] {"22","29","45","50","56h","72","112"};
    // DSM Somatic Problems
    private static final String[] DSM_SOM_ITEMS = new String[] {"51","56a","56b","56c","56d","56e","56f","56g","56i"};
    // DSM Avoidant Personality Problems
    private static final String[] DSM_AVOID_ITEMS = new String[] {"25","42","47","67","71","75","111"};
    // DSM Inattention Problems
    private static final String[] DSM_INATT_ITEMS = new String[] {"1","8","59","61","105","108","119"};
    // DSM Hyperactivity-Impulsivity Problems
    private static final String[] DSM_HYP_ITEMS = new String[] {"10","36","41","89","115","118"};
    // DSM Antisocial Personality Problems
    private static final String[] DSM_ASOC_ITEMS = new String[] {"3","5","16","21","23","26","28","37","39","43","57","76","82","92","95","97","101","114","120","122"};
    // DSM AD/H Problems (Sum of derived items)
    private static final String[] DSM_ADH_ITEMS = new String[] {"DSM_inatt_raw","DSM_hyp_raw"};
    
	private static final int[] T_ANXDP_F_1835 = new int[] {50,50,50,50,50,51,52,53,54,56,
												 		   58,59,60,62,63,64,65,66,68,69,
												 		   70,72,74,76,78,79,81,83,85,87,
												 		   89,91,93,94,96,98,100};
	private static final int[] T_ANXDP_F_3659 = new int[] {50,50,50,50,51,52,53,55,56,58,
												 		   59,61,62,64,65,66,67,68,69,70,
												 		   72,74,75,77,79,81,82,84,86,88,
												 		   89,91,93,95,96,98,100};
	private static final int[] T_ANXDP_M_1835 = new int[] {50,50,50,50,51,52,53,54,56,58,
												 		   60,61,62,63,64,65,67,68,69,70,
												 		   73,74,75,77,79,81,82,84,86,88,
												 		   89,91,93,95,96,98,100};
	private static final int[] T_ANXDP_M_3659 = new int[] {50,50,50,51,52,54,56,58,59,60,
												 		   61,63,64,65,66,67,69,70,72,73,
												 		   75,76,78,79,81,83,84,86,87,89,
												 		   91,92,94,95,97,98,100};
    
	private static final int[] T_WTHDP_F_1835 = new int[] {50,50,51,55,59,62,65,67,69,70,
												 		   73,77,80,83,87,90,93,96,100};
	private static final int[] T_WTHDP_F_3659 = new int[] {50,51,53,56,59,62,65,67,69,70,
												 		   73,77,80,83,87,90,93,96,100};
	private static final int[] T_WTHDP_M_1835 = new int[] {50,50,51,54,57,60,63,66,69,70,
												 		   73,77,80,83,87,90,93,97,100};
	private static final int[] T_WTHDP_M_3659 = new int[] {50,50,51,54,58,61,63,66,68,70,
												 		   73,77,80,83,87,90,93,97,100};
    
	private static final int[] T_SOM_F_1835 = new int[] {50,50,51,54,56,58,60,62,65,67,
												 		 69,70,72,75,77,79,82,84,86,88,
												 		 91,93,95,98,100};
	private static final int[] T_SOM_F_3659 = new int[] {50,51,52,54,57,59,61,63,65,67,
												 		 68,69,70,73,75,78,80,83,85,88,
												 		 90,93,95,98,100};
	private static final int[] T_SOM_M_1835 = new int[] {50,51,52,55,58,60,62,64,66,69,
												 		 70,72,74,76,79,81,83,85,87,89,
												 		 91,94,96,98,100};
	private static final int[] T_SOM_M_3659 = new int[] {50,51,53,57,60,62,64,65,67,69,
												 		 70,72,74,76,79,81,83,85,87,89,
												 		 91,94,96,98,100};
    
	private static final int[] T_THO_F_1835 = new int[] {50,50,51,55,58,62,65,68,70,73,
												 		 75,78,80,83,85,87,90,93,95,98,
												 		 100};
	private static final int[] T_THO_F_3659 = new int[] {50,51,54,58,62,65,68,70,72,75,
												 		 77,79,82,84,86,88,91,93,95,98,
												 		 100};
	private static final int[] T_THO_M_1835 = new int[] {50,50,52,55,59,62,65,68,70,73,
												 		 75,78,80,83,85,87,90,93,95,98,
												 		 100};
	private static final int[] T_THO_M_3659 = new int[] {50,51,54,59,63,66,68,70,72,75,
												 		 77,79,82,84,86,88,91,93,95,98,
												 		 100};
    
	private static final int[] T_ATT_F_1835 = new int[] {50,50,50,50,50,51,53,56,57,58,
												 		 59,60,61,63,66,69,70,72,73,76,
												 		 79,81,83,85,87,88,91,94,96,98,
												 		 100};
	private static final int[] T_ATT_F_3659 = new int[] {50,50,50,51,52,53,54,57,59,61,
												 		 63,64,65,66,68,69,70,72,74,76,
												 		 79,81,83,85,87,89,91,94,96,98,
												 		 100};
	private static final int[] T_ATT_M_1835 = new int[] {50,50,50,51,52,54,55,56,57,58,
												 		 59,60,61,63,64,66,67,68,69,70,
												 		 73,75,77,81,84,86,89,92,95,97,
												 		 100};
	private static final int[] T_ATT_M_3659 = new int[] {50,50,50,51,52,53,55,56,58,60,
												 		 62,64,66,68,70,72,74,76,78,79,
												 		 81,83,85,87,89,91,93,94,96,98,
												 		 100};
    
	private static final int[] T_AGG_F_1835 = new int[] {50,50,50,51,52,53,54,55,58,60,
												 		 61,62,63,65,66,67,68,69,70,73,
												 		 75,78,80,83,85,88,90,93,95,98,
												 		 100};
	private static final int[] T_AGG_F_3659 = new int[] {50,50,51,52,53,56,58,60,62,63,
												 		 65,67,68,69,70,72,74,76,78,79,
												 		 81,83,85,87,89,91,93,94,96,98,
												 		 100};
	private static final int[] T_AGG_M_1835 = new int[] {50,50,50,51,52,53,54,57,59,60,
												 		 61,62,63,64,65,67,68,69,70,73,
												 		 75,78,80,83,85,87,90,93,95,98,
												 		 100};
	private static final int[] T_AGG_M_3659 = new int[] {50,50,51,52,53,56,58,59,61,62,
												 		 64,66,68,70,72,74,75,77,79,81,
												 		 82,84,86,88,89,91,93,95,96,98,
												 		 100};
    
	private static final int[] T_RULE_F_1835 = new int[] {50,51,52,56,58,60,62,64,65,67,
												 		  70,72,73,75,77,78,80,82,83,85,
												 		  87,88,90,92,93,95,97,98,100};
	private static final int[] T_RULE_F_3659 = new int[] {50,51,55,59,63,67,70,71,73,74,
												 		  75,77,78,80,81,82,84,85,86,88,
												 		  89,90,92,93,95,96,97,99,100};
	private static final int[] T_RULE_M_1835 = new int[] {50,50,51,54,56,57,59,61,64,65,
												 		  67,69,70,72,74,76,78,79,81,83,
												 		  85,87,89,91,93,94,96,98,100};
	private static final int[] T_RULE_M_3659 = new int[] {50,51,53,56,59,61,63,66,69,70,
												 		  72,73,75,76,78,79,81,83,84,86,
												 		  87,89,91,92,94,95,97,98,100};
    
	private static final int[] T_INT_F_1835 = new int[] {50,50,51,54,57,61,65,67,70,73,
												 		 75,78,80};
	private static final int[] T_INT_F_3659 = new int[] {50,51,53,56,60,65,68,70,72,74,
												 		 76,78,80};
	private static final int[] T_INT_M_1835 = new int[] {50,50,51,53,56,60,63,67,70,73,
												 		 75,78,80};
	private static final int[] T_INT_M_3659 = new int[] {50,51,52,55,58,61,65,69,70,73,
												 		 75,78,80};
    
	private static final int[] T_DSM_DEP_F_1835 = new int[] {50,50,50,51,52,53,56,58,60,62,
												 		     63,65,67,69,70,72,74,76,79,81,
												 		     83,85,87,89,91,94,96,98,100};
	private static final int[] T_DSM_DEP_F_3659 = new int[] {50,50,50,51,52,55,57,59,61,63,
												 		     65,67,68,69,70,72,74,76,79,81,
												 		     83,85,87,89,91,94,96,98,100};
	private static final int[] T_DSM_DEP_M_1835 = new int[] {50,50,50,51,52,55,57,59,61,63,
												 		     65,67,69,70,72,74,76,78,80,82,
												 		     84,86,87,90,92,94,96,98,100};
	private static final int[] T_DSM_DEP_M_3659 = new int[] {50,50,51,52,55,58,60,62,65,66,
												 		     68,69,70,72,74,76,78,79,81,83,
												 		     85,87,89,91,93,94,96,98,100};
    
	private static final int[] T_DSM_ANX_F_1835 = new int[] {50,50,50,50,51,52,55,58,62,64,
												 		     67,70,73,77,80};
	private static final int[] T_DSM_ANX_F_3659 = new int[] {50,50,50,51,52,53,56,59,62,65,
												 		     69,70,73,77,80};
	private static final int[] T_DSM_ANX_M_1835 = new int[] {50,50,50,51,52,54,58,61,63,66,
												 		     69,70,73,77,80};
	private static final int[] T_DSM_ANX_M_3659 = new int[] {50,50,50,51,52,56,59,63,66,69,
												 		     70,73,75,78,80};
    
	private static final int[] T_DSM_SOM_F_1835 = new int[] {50,51,55,58,60,62,65,67,70,73,
												 		     76,79,82,85,87,91,94,97,100};
	private static final int[] T_DSM_SOM_F_3659 = new int[] {50,51,55,58,61,63,66,67,69,70,
												 		     73,77,80,83,87,90,93,97,100};
	private static final int[] T_DSM_SOM_M_1835 = new int[] {50,52,57,61,63,66,68,69,70,73,
												 		     76,79,82,85,88,91,94,97,100};
	private static final int[] T_DSM_SOM_M_3659 = new int[] {50,53,57,61,64,66,68,70,73,75,
												 		     78,81,84,86,89,92,95,97,100};
    
	private static final int[] T_DSM_AVOID_F_1835 = new int[] {50,50,51,53,57,61,63,67,70,73,
												 		       77,80,83,87,90};
	private static final int[] T_DSM_AVOID_F_3659 = new int[] {50,50,51,54,57,61,64,67,70,73,
												 		       77,80,83,87,90};
	private static final int[] T_DSM_AVOID_M_1835 = new int[] {50,50,51,54,57,60,64,67,70,73,
												 		       77,80,83,87,90};
	private static final int[] T_DSM_AVOID_M_3659 = new int[] {50,51,52,55,59,62,65,69,70,73,
												 		       77,80,83,87,90};
    
	private static final int[] T_DSM_ADH_F_1835 = new int[] {50,50,50,50,51,52,53,55,57,59,
												 		     61,64,67,69,70,73,75,78,80,83,
												 		     85,88,90,93,95,98,100};
	private static final int[] T_DSM_ADH_F_3659 = new int[] {50,50,50,51,52,53,56,58,60,62,
												 		     64,66,68,69,70,73,75,78,80,83,
												 		     85,88,90,93,95,98,100};
	private static final int[] T_DSM_ADH_M_1835 = new int[] {50,50,50,51,52,53,55,57,59,60,
												 		     61,63,64,65,67,68,69,70,73,77,
												 		     80,83,87,90,93,97,100};
	private static final int[] T_DSM_ADH_M_3659 = new int[] {50,50,50,51,52,53,55,57,59,61,
												 		     64,66,68,69,70,73,75,78,80,83,
												 		     85,87,90,93,95,98,100};
    
	private static final int[] T_DSM_ASOC_F_1835 = new int[] {50,50,51,52,54,56,59,61,62,64,
												 		      66,67,68,69,70,71,72,73,75,76,
												 		      77,78,79,80,82,83,84,85,86,87,
												 		      88,90,91,92,93,94,95,97,98,99,
												 		      100};
	private static final int[] T_DSM_ASOC_F_3659 = new int[] {50,51,52,55,58,61,63,66,68,70,
												 		      71,72,73,74,75,76,77,78,79,80,
												 		      81,82,83,84,85,85,86,87,88,89,
												 		      90,91,92,93,94,95,96,97,98,99,
												 		      100};
	private static final int[] T_DSM_ASOC_M_1835 = new int[] {50,50,50,51,52,54,57,58,60,62,
												 		      64,65,68,70,71,72,73,74,76,77,
												 		      78,79,80,82,83,84,85,86,87,88,
												 		      89,90,91,92,93,94,96,97,98,99,
												 		      100};
	private static final int[] T_DSM_ASOC_M_3659 = new int[] {50,50,51,52,55,58,61,63,65,67,
												 		      68,69,70,71,72,73,74,75,76,78,
												 		      79,80,81,82,83,84,85,86,87,88,
												 		      89,90,91,93,94,95,96,97,98,99,
												 		      100};
    
	private static final int[] T_COMP_INTERNALIZING_F_1835 = new int[] {30,32,36,39,40,42,43,45,46,48,
												 					    49,51,52,53,54,55,56,57,58,59,
												 		 			    60,61,62,63,63,64,64,65,66,67,
												 		    			68,69,70,71,72,72,73,74,74,75,
												 		    			76,76,77,77,78,79,79,80,81,81,
												 		    			82,83,83,84,84,85,86,86,87,88,
												 		    			88,89,90,91,91,92,93,93,94,94,
												 		    			95,96,96,97,98,98,99,99,100};
	
	private static final int[] T_COMP_INTERNALIZING_F_3659 = new int[] {30,34,38,40,43,45,46,48,49,51,
												 					    52,53,54,55,56,57,58,58,59,60,
												 		 			    61,62,62,63,64,65,66,67,67,68,
												 		    			68,69,69,70,71,72,73,74,75,76,
												 		    			77,78,79,80,81,82,83,84,85,86,
												 		    			87,88,89,90,90,91,91,92,92,92,
												 		    			93,93,94,94,94,95,95,96,96,96,
												 		    			97,97,98,98,98,99,99,100,100};
	
	private static final int[] T_COMP_INTERNALIZING_M_1835 = new int[] {30,31,35,38,41,43,45,47,48,50,
												 					    51,53,54,55,57,58,59,59,60,61,
												 		 			    61,62,63,63,64,64,65,65,66,67,
												 		    			67,68,68,69,70,71,72,73,73,74,
												 		    			75,76,77,78,78,79,80,81,82,82,
												 		    			83,84,85,86,87,87,88,89,90,91,
												 		    			91,92,92,93,93,94,94,95,95,96,
												 		    			96,97,97,98,98,99,99,100,100};
	
	private static final int[] T_COMP_INTERNALIZING_M_3659 = new int[] {30,34,37,41,44,46,48,50,51,53,
												 					    54,55,56,57,58,59,60,61,62,63,
												 		 			    64,64,65,65,66,66,67,67,68,69,
												 		    			70,71,72,72,73,74,75,75,76,77,
												 		    			77,78,79,80,80,81,82,83,83,84,
												 		    			85,85,86,87,88,88,89,90,90,91,
												 		    			91,92,92,93,93,94,94,95,95,96,
												 		    			96,97,97,98,98,99,99,100,100};
	
    
	private static final int[] T_COMP_EXTERNALIZING_F_1835 = new int[] {30,34,38,41,43,46,47,48,50,52,
												 					    53,54,55,56,57,58,59,60,61,62,
												 		 			    63,63,64,65,66,67,67,68,68,69,
												 		    			69,70,71,72,73,74,75,76,77,78,
												 		    			79,80,81,82,83,84,85,87,88,89,
												 		    			90,91,91,92,92,93,93,94,94,95,
												 		    			95,96,96,97,97,98,98,99,99,100,
												 		    			100};
	
	private static final int[] T_COMP_EXTERNALIZING_F_3659 = new int[] {32,38,42,45,46,48,50,52,53,55,
												 					    57,58,59,60,61,62,63,65,66,67,
												 		 			    68,69,70,71,72,73,75,76,77,78,
												 		    			79,81,82,83,84,85,87,88,89,90,
												 		    			90,91,91,91,92,92,92,93,93,93,
												 		    			94,94,94,95,95,95,95,96,96,96,
												 		    			97,97,97,98,98,98,99,99,99,100,
												 		    			100};
	
	private static final int[] T_COMP_EXTERNALIZING_M_1835 = new int[] {30,34,38,41,44,46,47,48,49,50,
												 					    52,53,54,55,56,57,58,58,59,60,
												 		 			    62,63,63,64,64,65,66,67,67,68,
												 		    			69,70,70,71,72,73,73,74,75,76,
												 		    			76,77,78,79,80,80,81,82,83,84,
												 		    			84,85,86,87,87,88,89,90,91,91,
												 		    			92,93,94,94,95,96,97,98,98,99,
												 		    			100};
	
	private static final int[] T_COMP_EXTERNALIZING_M_3659 = new int[] {32,38,41,44,46,48,49,51,52,54,
												 					    55,56,57,58,59,60,61,62,63,64,
												 		 			    65,66,67,67,68,69,70,71,72,73,
												 		    			75,76,77,78,79,81,82,83,84,85,
												 		    			87,88,89,90,90,91,91,91,92,92,
												 		    			93,93,93,94,94,94,95,95,96,96,
												 		    			96,97,97,97,98,98,99,99,99,100,
												 		    			100};
	
    
	private static final int[] T_COMP_TOTAL_F_1835 = new int[] {25,25,25,26,27,29,30,31,32,33,
												 				34,34,35,36,37,38,39,39,40,40,
												 		 	    41,41,42,43,44,44,45,45,45,46,
												 		  		46,47,47,48,48,48,49,49,49,50,
												 		  		50,51,51,52,52,52,53,53,53,53,
												 		  		54,54,54,55,55,56,56,56,57,57,
												 		  		57,58,58,58,58,59,59,59,60,60,
												 		  		60,61,61,61,62,62,62,62,63,63,
												 		  		63,63,64,64,64,64,64,65,65,65,
												 		  		66,66,66,67,67,67,68,68,68,69,
												 		  		69,69,69,70,70,71,71,71,72,72,
												 		  		72,72,73,73,73,73,74,74,74,74,
												 		  		75,75,75,75,76,76,76,76,77,77,
												 		  		77,77,78,78,78,78,79,79,79,79,
												 		  		80,80,80,80,81,81,81,81,82,82,
												 		  		82,82,83,83,83,83,84,84,84,84,
												 		  		85,85,85,85,86,86,86,86,87,87,
												 		  		87,87,88,88,88,88,89,89,89,90,
												 		  		90,90,90,91,91,91,91,91,91,92,
												 		  		92,92,92,92,92,93,93,93,93,93,
												 		  		93,94,94,94,94,94,94,95,95,95,
												 		  		95,95,95,96,96,96,96,96,96,97,
												 		  		97,97,97,97,97,98,98,98,98,98,
												 		  		98,99,99,99,99,99,99,100,100,100,
												 		  		100};
	
	private static final int[] T_COMP_TOTAL_F_3659 = new int[] {25,25,26,27,30,31,32,33,34,35,
												 				36,37,38,39,40,41,41,42,43,44,
												 		 	    44,45,45,46,46,47,47,48,48,49,
												 		  		49,50,50,50,51,51,52,52,52,53,
												 		  		53,53,54,54,55,55,56,56,56,57,
												 		  		57,57,58,58,58,59,59,59,60,60,
												 		  		60,61,61,62,62,62,63,63,63,64,
												 		  		64,64,64,65,65,65,65,66,66,66,
												 		  		67,67,67,67,68,68,68,68,68,68,
												 		  		69,69,69,69,69,69,70,70,71,72,
												 		  		72,73,73,74,74,75,75,76,76,77,
												 		  		77,78,78,79,79,80,80,81,81,82,
												 		  		82,83,83,84,84,85,85,86,86,87,
												 		  		87,88,88,89,90,90,90,90,90,90,
												 		  		91,91,91,91,91,91,91,91,91,91,
												 		  		92,92,92,92,92,92,92,92,92,92,
												 		  		92,93,93,93,93,93,93,93,93,93,
												 		  		93,93,94,94,94,94,94,94,94,94,
												 		  		94,94,95,95,95,95,95,95,95,95,
												 		  		95,95,95,96,96,96,96,96,96,96,
												 		  		96,96,96,97,97,97,97,97,97,97,
												 		  		97,97,97,97,98,98,98,98,98,98,
												 		  		98,98,98,98,98,99,99,99,99,99,
												 		  		99,99,99,99,99,100,100,100,100,100,
												 		  		100};
	
    
	private static final int[] T_COMP_TOTAL_M_1835 = new int[] {25,26,26,26,27,29,30,31,32,34,
												 				35,36,37,37,38,39,40,40,41,41,
												 		 	    42,42,43,44,44,45,45,46,46,47,
												 		  		47,48,48,48,49,49,49,50,50,51,
												 		  		51,51,52,52,52,53,53,53,54,54,
												 		  		54,55,55,55,55,56,56,56,57,57,
												 		  		57,58,58,58,59,59,59,59,60,60,
												 		  		60,60,61,61,61,61,61,62,62,62,
												 		  		62,62,63,63,63,63,64,64,64,64,
												 		  		64,65,65,65,66,66,66,67,67,67,
												 		  		67,67,68,68,68,68,69,69,69,70,
												 		  		70,70,70,71,71,72,72,72,72,73,
												 		  		73,73,73,74,74,74,74,75,75,75,
												 		  		76,76,76,76,77,77,77,77,78,78,
												 		  		78,78,79,79,79,79,80,80,80,81,
												 		  		81,81,81,82,82,82,82,83,83,83,
												 		  		83,84,84,84,85,85,85,85,86,86,
												 		  		86,86,87,87,87,87,88,88,88,88,
												 		  		89,89,90,90,90,91,91,91,91,91,
												 		  		91,92,92,92,92,92,92,93,93,93,
												 		  		93,93,93,94,94,94,94,94,94,95,
												 		  		95,95,95,95,96,96,96,96,96,96,
												 		  		97,97,97,97,97,97,98,98,98,98,
												 		  		98,98,99,99,99,99,99,99,100,100,
												 		  		100};
	
	private static final int[] T_COMP_TOTAL_M_3659 = new int[] {25,25,26,27,28,30,32,33,34,35,
												 				36,37,38,39,39,40,41,42,43,44,
												 		 	    44,45,46,46,47,48,48,49,49,50,
												 		  		50,50,51,51,52,52,53,53,54,54,
												 		  		54,55,55,56,56,56,57,57,57,57,
												 		  		58,58,58,58,59,59,59,60,60,60,
												 		  		61,61,62,62,62,62,63,63,63,63,
												 		  		64,64,64,64,64,65,65,65,66,66,
												 		  		66,67,67,68,68,69,69,70,70,71,
												 		  		72,72,73,73,74,74,75,75,76,77,
												 		  		77,78,78,79,79,80,80,81,82,82,
												 		  		83,83,84,84,85,86,86,87,87,88,
												 		  		88,89,89,90,90,90,90,90,90,91,
												 		  		91,91,91,91,91,91,91,91,91,91,
												 		  		91,92,92,92,92,92,92,92,92,92,
												 		  		92,92,92,93,93,93,93,93,93,93,
												 		  		93,93,93,93,94,94,94,94,94,94,
												 		  		94,94,94,94,94,94,95,95,95,95,
												 		  		95,95,95,95,95,95,95,95,96,96,
												 		  		96,96,96,96,96,96,96,96,96,96,
												 		  		97,97,97,97,97,97,97,97,97,97,
												 		  		97,98,98,98,98,98,98,98,98,98,
												 		  		98,98,98,99,99,99,99,99,99,99,
												 		  		99,99,99,99,99,99,100,100,100,100,
												 		  		100};
	
    /**
     * This is the main method that reads from a rawData map (output of CSV file parser) and returns a map of derived values
     */
	public static Map<String,String> computeScores(Map<String,String> rawData) {
		
		Map<String,String> scoreMap = new HashMap<String,String>();
		
		scoreMap.put("ASR_anxdp_raw",sumRawItems(rawData,ANXDP_ITEMS));
		scoreMap.put("ASR_wthdp_raw",sumRawItems(rawData,WTHDP_ITEMS));
		scoreMap.put("ASR_som_raw",sumRawItems(rawData,SOM_ITEMS));
		scoreMap.put("ASR_tho_raw",sumRawItems(rawData,THO_ITEMS));
		scoreMap.put("ASR_att_raw",sumRawItems(rawData,ATT_ITEMS));
		scoreMap.put("ASR_agg_raw",sumRawItems(rawData,AGG_ITEMS));
		scoreMap.put("ASR_rule_raw",sumRawItems(rawData,RULE_ITEMS));
		scoreMap.put("ASR_int_raw",sumRawItems(rawData,INT_ITEMS));
		scoreMap.put("ASR_other_raw",sumRawItems(rawData,OTHER_ITEMS));
		scoreMap.put("ASR_critical_raw",sumRawItems(rawData,CRITICAL_ITEMS));
		scoreMap.put("DSM_dep_raw",sumRawItems(rawData,DSM_DEP_ITEMS));
		scoreMap.put("DSM_anx_raw",sumRawItems(rawData,DSM_ANX_ITEMS));
		scoreMap.put("DSM_som_raw",sumRawItems(rawData,DSM_SOM_ITEMS));
		scoreMap.put("DSM_avoid_raw",sumRawItems(rawData,DSM_AVOID_ITEMS));
		scoreMap.put("DSM_inatt_raw",sumRawItems(rawData,DSM_INATT_ITEMS));
		scoreMap.put("DSM_hyp_raw",sumRawItems(rawData,DSM_HYP_ITEMS));
		scoreMap.put("DSM_asoc_raw",sumRawItems(rawData,DSM_ASOC_ITEMS));
		
		scoreMap.put("ASR_computed_internalizing_raw",sumDerivedItems(scoreMap,COMP_INTERNALIZING_ITEMS));
		scoreMap.put("ASR_computed_externalizing_raw",sumDerivedItems(scoreMap,COMP_EXTERNALIZING_ITEMS));
		scoreMap.put("ASR_computed_other_raw",sumDerivedItems(scoreMap,COMP_OTHER_ITEMS));
		scoreMap.put("ASR_computed_total_raw",sumDerivedItems(scoreMap,COMP_TOTAL_ITEMS));
		
		scoreMap.put("DSM_adh_raw",sumDerivedItems(scoreMap,DSM_ADH_ITEMS));
		
		String sex = rawData.get("sex");
		int age=-1;
		try {
			age = Integer.parseInt(rawData.get("age"));
		} catch (NumberFormatException e) {
			// Do nothing for now.
		}
		if ((sex.equalsIgnoreCase("M") || sex.equalsIgnoreCase("F")) && age>0) {
			// Compute T Scores
			if (sex.equalsIgnoreCase("F") && age>=18 && age<=36) {
				scoreMap.put("ASR_anxdp_t",computeT(scoreMap,"ASR_anxdp_raw",T_ANXDP_F_1835));
				scoreMap.put("ASR_wthdp_t",computeT(scoreMap,"ASR_wthdp_raw",T_WTHDP_F_1835));
				scoreMap.put("ASR_som_t",computeT(scoreMap,"ASR_som_raw",T_SOM_F_1835));
				scoreMap.put("ASR_tho_t",computeT(scoreMap,"ASR_tho_raw",T_THO_F_1835));
				scoreMap.put("ASR_att_t",computeT(scoreMap,"ASR_att_raw",T_ATT_F_1835));
				scoreMap.put("ASR_agg_t",computeT(scoreMap,"ASR_agg_raw",T_AGG_F_1835));
				scoreMap.put("ASR_rule_t",computeT(scoreMap,"ASR_rule_raw",T_RULE_F_1835));
				scoreMap.put("ASR_int_t",computeT(scoreMap,"ASR_int_raw",T_INT_F_1835));
				scoreMap.put("ASR_computed_internalizing_t",computeT(scoreMap,"ASR_computed_internalizing_raw",T_COMP_INTERNALIZING_F_1835));
				scoreMap.put("ASR_computed_externalizing_t",computeT(scoreMap,"ASR_computed_externalizing_raw",T_COMP_EXTERNALIZING_F_1835));
				scoreMap.put("ASR_computed_total_t",computeT(scoreMap,"ASR_computed_total_raw",T_COMP_TOTAL_F_1835));
				scoreMap.put("DSM_dep_t",computeT(scoreMap,"DSM_dep_raw",T_DSM_DEP_F_1835));
				scoreMap.put("DSM_anx_t",computeT(scoreMap,"DSM_anx_raw",T_DSM_ANX_F_1835));
				scoreMap.put("DSM_som_t",computeT(scoreMap,"DSM_som_raw",T_DSM_SOM_F_1835));
				scoreMap.put("DSM_avoid_t",computeT(scoreMap,"DSM_avoid_raw",T_DSM_AVOID_F_1835));
				scoreMap.put("DSM_adh_t",computeT(scoreMap,"DSM_adh_raw",T_DSM_ADH_F_1835));
				scoreMap.put("DSM_asoc_t",computeT(scoreMap,"DSM_asoc_raw",T_DSM_ASOC_F_1835));
			} else if (sex.equalsIgnoreCase("F") && age<=59) {
				scoreMap.put("ASR_anxdp_t",computeT(scoreMap,"ASR_anxdp_raw",T_ANXDP_F_3659));
				scoreMap.put("ASR_wthdp_t",computeT(scoreMap,"ASR_wthdp_raw",T_WTHDP_F_3659));
				scoreMap.put("ASR_som_t",computeT(scoreMap,"ASR_som_raw",T_SOM_F_3659));
				scoreMap.put("ASR_tho_t",computeT(scoreMap,"ASR_tho_raw",T_THO_F_3659));
				scoreMap.put("ASR_att_t",computeT(scoreMap,"ASR_att_raw",T_ATT_F_3659));
				scoreMap.put("ASR_agg_t",computeT(scoreMap,"ASR_agg_raw",T_AGG_F_3659));
				scoreMap.put("ASR_rule_t",computeT(scoreMap,"ASR_rule_raw",T_RULE_F_3659));
				scoreMap.put("ASR_int_t",computeT(scoreMap,"ASR_int_raw",T_INT_F_3659));
				scoreMap.put("ASR_computed_internalizing_t",computeT(scoreMap,"ASR_computed_internalizing_raw",T_COMP_INTERNALIZING_F_3659));
				scoreMap.put("ASR_computed_externalizing_t",computeT(scoreMap,"ASR_computed_externalizing_raw",T_COMP_EXTERNALIZING_F_3659));
				scoreMap.put("ASR_computed_total_t",computeT(scoreMap,"ASR_computed_total_raw",T_COMP_TOTAL_F_3659));
				scoreMap.put("DSM_dep_t",computeT(scoreMap,"DSM_dep_raw",T_DSM_DEP_F_3659));
				scoreMap.put("DSM_anx_t",computeT(scoreMap,"DSM_anx_raw",T_DSM_ANX_F_3659));
				scoreMap.put("DSM_som_t",computeT(scoreMap,"DSM_som_raw",T_DSM_SOM_F_3659));
				scoreMap.put("DSM_avoid_t",computeT(scoreMap,"DSM_avoid_raw",T_DSM_AVOID_F_3659));
				scoreMap.put("DSM_adh_t",computeT(scoreMap,"DSM_adh_raw",T_DSM_ADH_F_3659));
				scoreMap.put("DSM_asoc_t",computeT(scoreMap,"DSM_asoc_raw",T_DSM_ASOC_F_3659));
			} else if (sex.equalsIgnoreCase("M") && age>=18 && age<=36) {
				scoreMap.put("ASR_anxdp_t",computeT(scoreMap,"ASR_anxdp_raw",T_ANXDP_M_1835));
				scoreMap.put("ASR_wthdp_t",computeT(scoreMap,"ASR_wthdp_raw",T_WTHDP_M_1835));
				scoreMap.put("ASR_som_t",computeT(scoreMap,"ASR_som_raw",T_SOM_M_1835));
				scoreMap.put("ASR_tho_t",computeT(scoreMap,"ASR_tho_raw",T_THO_M_1835));
				scoreMap.put("ASR_att_t",computeT(scoreMap,"ASR_att_raw",T_ATT_M_1835));
				scoreMap.put("ASR_agg_t",computeT(scoreMap,"ASR_agg_raw",T_AGG_M_1835));
				scoreMap.put("ASR_rule_t",computeT(scoreMap,"ASR_rule_raw",T_RULE_M_1835));
				scoreMap.put("ASR_int_t",computeT(scoreMap,"ASR_int_raw",T_INT_M_1835));
				scoreMap.put("ASR_computed_internalizing_t",computeT(scoreMap,"ASR_computed_internalizing_raw",T_COMP_INTERNALIZING_M_1835));
				scoreMap.put("ASR_computed_externalizing_t",computeT(scoreMap,"ASR_computed_externalizing_raw",T_COMP_EXTERNALIZING_M_1835));
				scoreMap.put("ASR_computed_total_t",computeT(scoreMap,"ASR_computed_total_raw",T_COMP_TOTAL_M_1835));
				scoreMap.put("DSM_dep_t",computeT(scoreMap,"DSM_dep_raw",T_DSM_DEP_M_1835));
				scoreMap.put("DSM_anx_t",computeT(scoreMap,"DSM_anx_raw",T_DSM_ANX_M_1835));
				scoreMap.put("DSM_som_t",computeT(scoreMap,"DSM_som_raw",T_DSM_SOM_M_1835));
				scoreMap.put("DSM_avoid_t",computeT(scoreMap,"DSM_avoid_raw",T_DSM_AVOID_M_1835));
				scoreMap.put("DSM_adh_t",computeT(scoreMap,"DSM_adh_raw",T_DSM_ADH_M_1835));
				scoreMap.put("DSM_asoc_t",computeT(scoreMap,"DSM_asoc_raw",T_DSM_ASOC_M_1835));
			} else if (sex.equalsIgnoreCase("M") && age<=59) {
				scoreMap.put("ASR_anxdp_t",computeT(scoreMap,"ASR_anxdp_raw",T_ANXDP_M_3659));
				scoreMap.put("ASR_wthdp_t",computeT(scoreMap,"ASR_wthdp_raw",T_WTHDP_M_3659));
				scoreMap.put("ASR_som_t",computeT(scoreMap,"ASR_som_raw",T_SOM_M_3659));
				scoreMap.put("ASR_tho_t",computeT(scoreMap,"ASR_tho_raw",T_THO_M_3659));
				scoreMap.put("ASR_att_t",computeT(scoreMap,"ASR_att_raw",T_ATT_M_3659));
				scoreMap.put("ASR_agg_t",computeT(scoreMap,"ASR_agg_raw",T_AGG_M_3659));
				scoreMap.put("ASR_rule_t",computeT(scoreMap,"ASR_rule_raw",T_RULE_M_3659));
				scoreMap.put("ASR_int_t",computeT(scoreMap,"ASR_int_raw",T_INT_M_3659));
				scoreMap.put("ASR_computed_internalizing_t",computeT(scoreMap,"ASR_computed_internalizing_raw",T_COMP_INTERNALIZING_M_3659));
				scoreMap.put("ASR_computed_externalizing_t",computeT(scoreMap,"ASR_computed_externalizing_raw",T_COMP_EXTERNALIZING_M_3659));
				scoreMap.put("ASR_computed_total_t",computeT(scoreMap,"ASR_computed_total_raw",T_COMP_TOTAL_M_3659));
				scoreMap.put("DSM_dep_t",computeT(scoreMap,"DSM_dep_raw",T_DSM_DEP_M_3659));
				scoreMap.put("DSM_anx_t",computeT(scoreMap,"DSM_anx_raw",T_DSM_ANX_M_3659));
				scoreMap.put("DSM_som_t",computeT(scoreMap,"DSM_som_raw",T_DSM_SOM_M_3659));
				scoreMap.put("DSM_avoid_t",computeT(scoreMap,"DSM_avoid_raw",T_DSM_AVOID_M_3659));
				scoreMap.put("DSM_adh_t",computeT(scoreMap,"DSM_adh_raw",T_DSM_ADH_M_3659));
				scoreMap.put("DSM_asoc_t",computeT(scoreMap,"DSM_asoc_raw",T_DSM_ASOC_M_3659));
			}
		}
		
		return scoreMap;

	}

	private static String computeT(Map<String, String> scoreMap, String rawScoreItem, int[] itemT) {
		try {
			return Integer.toString(itemT[Integer.parseInt(scoreMap.get(rawScoreItem))]);
		} catch (NumberFormatException nfe) {
			return "";
		} catch (ArrayIndexOutOfBoundsException aoe) {
			return "";
		}
	}

	private static String sumRawItems(Map<String, String> rawData, String[] items) {
		try {
			int sumVar=0;
			for (String item : items) {
				sumVar=sumVar+Integer.parseInt(rawData.get(raw_prepend + addLeadingZeros(item,3)));
			}
			return new Integer(sumVar).toString();
		} catch (Exception e) {	
			return "";
		}
	}

	private static String sumDerivedItems(Map<String, String> scoreData, String[] items) {
		try {
			int sumVar=0;
			for (String item : items) {
				sumVar=sumVar+Integer.parseInt(scoreData.get(item));
			}
			return new Integer(sumVar).toString();
		} catch (Exception e) {	
			return "";
		}
	}

	private static String addLeadingZeros(String item, int i) {
		StringBuilder sb = new StringBuilder(item);
		while (sb.toString().replaceAll("[^0-9]","").length()<i) {
			sb.insert(0,'0');
		}
		return sb.toString();
	}

}
